/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package com.alibaba.cloud.ai.dataagent.service.datasource.impl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO.ColumnCommentDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceCommentsDTO.TableCommentDTO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceCommentService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/** JDBC implementation for transactional comment updates. */
@Service
@RequiredArgsConstructor
public class DatasourceCommentServiceImpl implements DatasourceCommentService {

	private final DatasourceService datasourceService;

	private final DBConnectionPoolFactory connectionPoolFactory;

	@Override
	public void updateComments(Integer datasourceId, UpdateDatasourceCommentsDTO request) throws Exception {
		if (datasourceId == null || request == null || request.getTables() == null) {
			throw new IllegalArgumentException("数据源和注释变更不能为空");
		}
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new IllegalArgumentException("数据源不存在: " + datasourceId);
		}
		if (request.getTables().isEmpty()) {
			return;
		}
		DbConfigBO config = datasourceService.getDbConfig(datasource);
		try (Connection connection = connectionPoolFactory.getPoolByDbType(datasource.getType()).getConnection(config)) {
			boolean autoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			try {
				for (TableCommentDTO table : request.getTables()) {
					applyTable(connection, datasource.getType(), table);
				}
				connection.commit();
			}
			catch (Exception exception) {
				connection.rollback();
				throw exception;
			}
			finally {
				connection.setAutoCommit(autoCommit);
			}
		}
	}

	private void applyTable(Connection connection, String datasourceType, TableCommentDTO table) throws SQLException {
		String tableName = identifier(table.getName(), "表名");
		String schema = StringUtils.trimToNull(table.getSchema());
		String qualifiedTable = qualifiedIdentifier(schema, tableName, datasourceType);
		String comment = table.getDescription() == null ? "" : table.getDescription();
		if (BizDataSourceTypeEnum.isMysqlDialect(datasourceType)) {
			executeComment(connection, "ALTER TABLE " + qualifiedTable + " COMMENT = ?", comment);
		}
		else if (supportsCommentOn(datasourceType)) {
			executeComment(connection, "COMMENT ON TABLE " + qualifiedTable + " IS ?", comment);
		}
		else {
			throw new UnsupportedOperationException("当前数据库暂不支持修改表注释: " + datasourceType);
		}
		List<ColumnCommentDTO> columns = table.getColumns();
		if (columns == null) {
			return;
		}
		for (ColumnCommentDTO column : columns) {
			String columnName = identifier(column.getName(), "列名");
			String columnComment = column.getDescription() == null ? "" : column.getDescription();
			if (BizDataSourceTypeEnum.isMysqlDialect(datasourceType)) {
				applyMysqlColumnComment(connection, schema, tableName, columnName, columnComment);
			}
			else if (supportsCommentOn(datasourceType)) {
				executeComment(connection, "COMMENT ON COLUMN " + qualifiedTable + "." + quoteIdentifier(columnName,
					datasourceType) + " IS ?", columnComment);
			}
		}
	}

	private void applyMysqlColumnComment(Connection connection, String schema, String tableName, String columnName,
			String comment) throws SQLException {
		String catalog = schema == null ? connection.getCatalog() : schema;
		String columnType = null;
		String nullable = null;
		String defaultValue = null;
		String extra = null;
		String collation = null;
		String characterSet = null;
		String sql = "SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME "
				+ "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, catalog);
			statement.setString(2, tableName);
			statement.setString(3, columnName);
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next()) {
					throw new IllegalArgumentException("列不存在: " + tableName + "." + columnName);
				}
				columnType = result.getString(1);
				nullable = result.getString(2);
				defaultValue = result.getString(3);
				extra = result.getString(4);
				characterSet = result.getString(5);
				collation = result.getString(6);
			}
		}
		StringBuilder definition = new StringBuilder("ALTER TABLE ")
			.append(qualifiedIdentifier(schema, tableName, "mysql"))
			.append(" MODIFY COLUMN ").append(quoteIdentifier(columnName, "mysql"))
			.append(" ").append(columnType);
		if ("NO".equalsIgnoreCase(nullable)) {
			definition.append(" NOT NULL");
		}
		if (defaultValue != null) {
			definition.append(" DEFAULT ").append(mysqlDefaultExpression(defaultValue));
		}
		if (StringUtils.isNotBlank(characterSet)) {
			definition.append(" CHARACTER SET ").append(identifier(characterSet, "字符集"));
		}
		if (StringUtils.isNotBlank(collation)) {
			definition.append(" COLLATE ").append(identifier(collation, "排序规则"));
		}
		if (StringUtils.isNotBlank(extra)) {
			String normalizedExtra = extra.replace("DEFAULT_GENERATED", "").trim();
			if (normalizedExtra.toLowerCase(Locale.ROOT).startsWith("on update")
					|| normalizedExtra.equalsIgnoreCase("auto_increment")) {
				definition.append(" ").append(normalizedExtra);
			}
		}
		definition.append(" COMMENT ?");
		executeComment(connection, definition.toString(), comment);
	}

	private void executeComment(Connection connection, String sql, String comment) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, comment);
			statement.executeUpdate();
		}
	}

	private boolean supportsCommentOn(String datasourceType) {
		String type = datasourceType == null ? "" : datasourceType.toLowerCase(Locale.ROOT);
		return type.equals("postgresql") || type.equals("postgresql-vpc") || type.equals("h2")
				|| type.equals("oracle") || type.equals("dameng") || type.equals("hologress");
	}

	private String qualifiedIdentifier(String schema, String table, String datasourceType) {
		return schema == null ? quoteIdentifier(table, datasourceType)
				: quoteIdentifier(schema, datasourceType) + "." + quoteIdentifier(table, datasourceType);
	}

	private String quoteIdentifier(String value, String datasourceType) {
		String quote = BizDataSourceTypeEnum.isMysqlDialect(datasourceType) ? "`" : "\"";
		return quote + value.replace(quote, quote + quote) + quote;
	}

	private String identifier(String value, String label) {
		String trimmed = StringUtils.trimToNull(value);
		if (trimmed == null || !trimmed.matches("[A-Za-z0-9_$-]+")) {
			throw new IllegalArgumentException(label + "包含非法字符");
		}
		return trimmed;
	}

	private String escapeLiteral(String value) {
		return value.replace("'", "''");
	}

	private String mysqlDefaultExpression(String value) {
		String trimmed = value.trim();
		if (trimmed.equalsIgnoreCase("NULL") || trimmed.matches("-?\\d+(\\.\\d+)?")
				|| trimmed.matches("(?i)(current_timestamp|current_date|current_time)(\\(\\d+\\))?")) {
			return trimmed;
		}
		return "'" + escapeLiteral(value) + "'";
	}

}
