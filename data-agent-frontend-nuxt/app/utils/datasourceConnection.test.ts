import { describe, expect, it } from 'vitest';
import { buildDatasourceConnectionUrl } from './datasourceConnection';

describe('buildDatasourceConnectionUrl', () => {
	it('builds the default MySQL URL used by the backend', () => {
		expect(
			buildDatasourceConnectionUrl({
				type: 'mysql',
				host: 'localhost',
				port: 3306,
				databaseName: 'sakila',
			}),
		).toBe(
			'jdbc:mysql://localhost:3306/sakila?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai',
		);
	});

	it('strips the schema suffix from PostgreSQL and Oracle database names', () => {
		expect(
			buildDatasourceConnectionUrl({
				type: 'postgresql',
				host: 'db.example.com',
				port: 5432,
				databaseName: 'analytics|reporting',
			}),
		).toContain('jdbc:postgresql://db.example.com:5432/analytics?');
		expect(
			buildDatasourceConnectionUrl({
				type: 'oracle',
				host: 'oracle.example.com',
				port: 1521,
				databaseName: 'ORCL|APP',
			}),
		).toBe('jdbc:oracle:thin:@oracle.example.com:1521/ORCL');
	});

	it('returns an empty URL until required fields are present', () => {
		expect(buildDatasourceConnectionUrl({ type: 'mysql', host: 'localhost', port: 3306 })).toBe('');
		expect(
			buildDatasourceConnectionUrl({
				type: 'mysql',
				host: 'localhost',
				port: 3306,
				databaseName: ' ',
			}),
		).toBe('');
	});
});
