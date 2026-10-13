package dev.practice.bank.shared;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @Test
    void cpfsGeradosSaoValidos() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            String cpf = Cpf.generate(random);
            assertThat(cpf).hasSize(11);
            assertThat(Cpf.isValid(cpf)).as(cpf).isTrue();
        }
    }

    @Test
    void recusaDigitoVerificadorErradoESequenciasRepetidas() {
        String valid = Cpf.generate(new Random(7));
        String wrongDigit = valid.substring(0, 10) + ((valid.charAt(10) - '0' + 1) % 10);
        assertThat(Cpf.isValid(wrongDigit)).isFalse();
        assertThat(Cpf.isValid("11111111111")).isFalse();
        assertThat(Cpf.isValid("123")).isFalse();
        assertThat(Cpf.isValid(null)).isFalse();
    }

    @Test
    void aceitaCpfFormatado() {
        String cpf = Cpf.generate(new Random(3));
        String formatted = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
        assertThat(Cpf.isValid(formatted)).isTrue();
    }

    @Test
    void mascaraEscondeOInicioEOFim() {
        assertThat(Cpf.mask("12345678909")).isEqualTo("***.456.789-**");
        assertThat(Cpf.mask("abc")).isEqualTo("***");
    }
}
