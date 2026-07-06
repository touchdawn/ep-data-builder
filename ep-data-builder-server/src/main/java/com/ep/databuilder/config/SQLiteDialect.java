package com.ep.databuilder.config;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;

import java.sql.Types;

/**
 * Hibernate 5.3 没有内置 SQLite 方言，自带一个最小实现。
 * schema 由 Flyway 管理（ddl-auto=none），本方言只需覆盖查询生成与主键回填。
 */
public class SQLiteDialect extends Dialect {

    public SQLiteDialect() {
        registerColumnType(Types.BIT, "integer");
        registerColumnType(Types.BOOLEAN, "integer");
        registerColumnType(Types.TINYINT, "integer");
        registerColumnType(Types.SMALLINT, "integer");
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.BIGINT, "integer");
        registerColumnType(Types.FLOAT, "real");
        registerColumnType(Types.REAL, "real");
        registerColumnType(Types.DOUBLE, "real");
        registerColumnType(Types.NUMERIC, "numeric");
        registerColumnType(Types.DECIMAL, "numeric");
        registerColumnType(Types.CHAR, "text");
        registerColumnType(Types.VARCHAR, "text");
        registerColumnType(Types.LONGVARCHAR, "text");
        registerColumnType(Types.DATE, "integer");
        registerColumnType(Types.TIME, "integer");
        registerColumnType(Types.TIMESTAMP, "integer");
        registerColumnType(Types.BINARY, "blob");
        registerColumnType(Types.VARBINARY, "blob");
        registerColumnType(Types.LONGVARBINARY, "blob");
        registerColumnType(Types.BLOB, "blob");
        registerColumnType(Types.CLOB, "text");
    }

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new IdentityColumnSupportImpl() {
            @Override
            public boolean supportsIdentityColumns() {
                return true;
            }

            @Override
            public String getIdentitySelectString(String table, String column, int type) {
                return "select last_insert_rowid()";
            }

            @Override
            public String getIdentityColumnString(int type) {
                return "integer";
            }
        };
    }

    @Override
    public boolean hasAlterTable() {
        return false;
    }

    @Override
    public boolean dropConstraints() {
        return false;
    }

    @Override
    public String getDropForeignKeyString() {
        return "";
    }

    @Override
    public String getAddForeignKeyConstraintString(String constraintName, String[] foreignKey,
                                                   String referencedTable, String[] primaryKey,
                                                   boolean referencesPrimaryKey) {
        return "";
    }

    @Override
    public String getAddPrimaryKeyConstraintString(String constraintName) {
        return "";
    }

    @Override
    public String getForUpdateString() {
        return "";
    }

    @Override
    public boolean supportsUnionAll() {
        return true;
    }

    @Override
    public boolean supportsIfExistsBeforeTableName() {
        return true;
    }

    @Override
    public boolean supportsCurrentTimestampSelection() {
        return true;
    }

    @Override
    public boolean isCurrentTimestampSelectStringCallable() {
        return false;
    }

    @Override
    public String getCurrentTimestampSelectString() {
        return "select current_timestamp";
    }
}
