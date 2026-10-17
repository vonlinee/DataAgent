export interface DatasourceConnectionFields {
	type?: string;
	host?: string;
	port?: number;
	databaseName?: string;
}

/** Build the same default JDBC URLs as the datasource type handlers. */
export function buildDatasourceConnectionUrl({
	type,
	host,
	port,
	databaseName,
}: DatasourceConnectionFields): string {
	const normalizedType = type?.toLowerCase();
	const normalizedHost = host?.trim();
	const normalizedDatabaseName = databaseName?.trim();
	const hasNetworkFields = Boolean(
		normalizedHost && port != null && Number.isFinite(Number(port)),
	);

	if (normalizedType === 'sqlite') {
		return normalizedDatabaseName ? `jdbc:sqlite:${normalizedDatabaseName}` : '';
	}
	if (!hasNetworkFields || !normalizedDatabaseName) return '';

	switch (normalizedType) {
		case 'mysql':
			return `jdbc:mysql://${normalizedHost}:${port}/${normalizedDatabaseName}?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai`;
		case 'postgresql':
			return `jdbc:postgresql://${normalizedHost}:${port}/${normalizedDatabaseName.split('|')[0]}?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai`;
		case 'sqlserver':
			return `jdbc:sqlserver://${normalizedHost}:${port};databaseName=${normalizedDatabaseName}`;
		case 'dameng':
			return `jdbc:dm://${normalizedHost}:${port}`;
		case 'oracle':
			return `jdbc:oracle:thin:@${normalizedHost}:${port}/${normalizedDatabaseName.split('|')[0]}`;
		case 'hive':
			return `jdbc:hive2://${normalizedHost}:${port}/${normalizedDatabaseName}`;
		case 'h2':
			return `jdbc:h2:mem:${normalizedDatabaseName};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE`;
		default:
			return '';
	}
}
