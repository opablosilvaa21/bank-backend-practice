package dev.practice.bank.shared;

import java.util.random.RandomGenerator;

/**
 * Utilitário de CPF: geração de números fictícios, validação dos dígitos e máscara.
 * Os CPFs gerados têm dígitos verificadores válidos, mas são aleatórios e servem só para teste.
 */
public final class Cpf {

    private Cpf() {}

    public static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static boolean isValid(String value) {
        String cpf = onlyDigits(value);
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        int[] d = cpf.chars().map(c -> c - '0').toArray();
        return d[9] == checkDigit(d, 9) && d[10] == checkDigit(d, 10);
    }

    public static String generate(RandomGenerator random) {
        int[] d = new int[11];
        do {
            for (int i = 0; i < 9; i++) {
                d[i] = random.nextInt(10);
            }
        } while (allEqual(d));
        d[9] = checkDigit(d, 9);
        d[10] = checkDigit(d, 10);
        StringBuilder sb = new StringBuilder(11);
        for (int digit : d) {
            sb.append(digit);
        }
        return sb.toString();
    }

    /** 12345678909 vira ***.456.789-** : nunca expõe o CPF completo na API. */
    public static String mask(String value) {
        String cpf = onlyDigits(value);
        if (cpf.length() != 11) {
            return "***";
        }
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }

    private static int checkDigit(int[] d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += d[i] * (length + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    private static boolean allEqual(int[] d) {
        for (int i = 1; i < 9; i++) {
            if (d[i] != d[0]) {
                return false;
            }
        }
        return true;
    }
}
