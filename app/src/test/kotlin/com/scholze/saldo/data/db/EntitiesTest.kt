package com.scholze.saldo.data.db

import com.scholze.saldo.domain.Movimentacao
import com.scholze.saldo.domain.Natureza
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitiesTest {
    private val base = Movimentacao(
        descricao = "m", valorCentavos = -1_00, data = LocalDate.parse("2026-07-20"), natureza = Natureza.DIARIO,
    )

    /** Linha nova: o carimbo é "agora". */
    @Test
    fun toEntityCarimbaAgoraQuandoCriadaEmEDesconhecido() {
        val antes = System.currentTimeMillis()
        assertTrue(base.toEntity().criadaEm >= antes)
    }

    /** `restaurar` (desfazer) reinsere o snapshot: a data de criação original tem de sobreviver. */
    @Test
    fun toEntityPreservaCriadaEmConhecido() {
        assertEquals(123L, base.copy(criadaEm = 123L).toEntity().criadaEm)
    }

    @Test
    fun toDomainCarregaCriadaEm() {
        val entity = MovimentacaoEntity(
            id = 1, descricao = "m", valorCentavos = -1_00, dataEpochDay = 20654, natureza = "DIARIO", criadaEm = 123L,
        )
        assertEquals(123L, MovimentacaoComTags(entity, emptyList()).toDomain().criadaEm)
    }
}
