package cn.tolim.pudans.communal.mybatis.sql;

import cn.tolim.pudans.communal.mybatis.page.PageQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PostgresDialectTests {

    @Test
    void quotesSchemaQualifiedIdentifiers() {
        assertThat(PostgresDialect.quoteIdentifier("public.pudans_user")).isEqualTo("\"public\".\"pudans_user\"");
    }

    @Test
    void rejectsInjectionInIdentifiers() {
        assertThatIllegalArgumentException().isThrownBy(() -> PostgresDialect.quoteIdentifier("user; drop table x"));
    }

    @Test
    void buildsLimitOffsetFromValidatedIntegers() {
        assertThat(PostgresDialect.limitOffset(new PageQuery(3, 20))).isEqualTo("LIMIT 20 OFFSET 40");
    }

    @Test
    void wrapsCountSqlAndRemovesTrailingSemicolon() {
        assertThat(PostgresDialect.countSql("SELECT * FROM pudans_user;"))
                .isEqualTo("SELECT COUNT(*) FROM (SELECT * FROM pudans_user) pudans_count");
    }

    @Test
    void escapesLikeWildcards() {
        assertThat(PostgresDialect.escapeLike("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
        assertThat(PostgresDialect.containsPattern("a%b")).isEqualTo("%a\\%b%");
    }

    @Test
    void validatesUuidLiterals() {
        assertThat(PostgresDialect.uuidLiteral("6f1d0d3e-6a1f-4a1b-9f4e-2c6a1d0b9e11"))
                .isEqualTo("'6f1d0d3e-6a1f-4a1b-9f4e-2c6a1d0b9e11'::uuid");
        assertThatIllegalArgumentException().isThrownBy(() -> PostgresDialect.uuidLiteral("not-a-uuid"));
    }

    @Test
    void appendsPaginationToSelect() {
        assertThat(PostgresDialect.pageSql("SELECT id FROM t;", new PageQuery(2, 10)))
                .isEqualTo("SELECT id FROM t LIMIT 10 OFFSET 10");
    }
}