package dev.practice.bank.config;

import dev.practice.bank.account.Account;
import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.account.AccountType;
import dev.practice.bank.customer.Customer;
import dev.practice.bank.customer.CustomerRepository;
import dev.practice.bank.pix.PixKey;
import dev.practice.bank.pix.PixKeyRepository;
import dev.practice.bank.pix.PixKeyType;
import dev.practice.bank.shared.Cpf;
import dev.practice.bank.transaction.AccountTransaction;
import dev.practice.bank.transaction.AccountTransactionRepository;
import dev.practice.bank.transaction.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Gera uma base 100% fictícia ao subir a aplicação: clientes, contas, extrato e chaves Pix.
 *
 * - Nomes são combinações aleatórias de listas comuns.
 * - CPFs têm dígitos verificadores válidos, mas são números aleatórios gerados aqui.
 * - E-mails usam o domínio "@gmail.com" (nome.sobrenome@gmail.com).
 * - O saldo de cada conta é exatamente a soma do seu extrato.
 * - A semente fixa (bank.demo.seed) faz a base sair igual em toda execução.
 *
 * As contas 1, 2 e 3 (Ana, Bruno e Carla) são fixas, com R$ 1.500, R$ 850 e R$ 3.200,
 * para os exemplos do README e os testes continuarem valendo.
 */
@Component
@ConditionalOnProperty(name = "bank.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private static final List<String> FIRST_NAMES = List.of(
            "Ana", "Bruno", "Carla", "Daniel", "Eduarda", "Felipe", "Gabriela", "Henrique", "Isabela", "João",
            "Juliana", "Lucas", "Mariana", "Mateus", "Natália", "Otávio", "Patrícia", "Rafael", "Renata", "Rodrigo",
            "Sabrina", "Thiago", "Vanessa", "Vinícius", "Larissa", "Gustavo", "Camila", "Leonardo", "Beatriz", "André",
            "Fernanda", "Diego", "Letícia", "Marcelo", "Aline", "Pedro", "Priscila", "Caio", "Tatiane", "Samuel");

    private static final List<String> LAST_NAMES = List.of(
            "Silva", "Santos", "Oliveira", "Souza", "Lima", "Pereira", "Costa", "Ferreira", "Rodrigues", "Almeida",
            "Nascimento", "Carvalho", "Araújo", "Ribeiro", "Gomes", "Martins", "Rocha", "Barbosa", "Melo", "Cardoso",
            "Teixeira", "Correia", "Mendes", "Moreira", "Nunes", "Freitas", "Vieira", "Monteiro", "Pinto", "Moura");

    private static final List<String> AGENCIES = List.of("0001", "0247", "1520", "3301", "4789");

    private static final List<String> PAYMENTS = List.of(
            "Pagamento de boleto - energia elétrica", "Pagamento de boleto - internet",
            "Pagamento de boleto - condomínio", "Pagamento de fatura do cartão",
            "Compra no débito - supermercado", "Compra no débito - farmácia",
            "Compra no débito - posto de combustível", "Compra no débito - restaurante");

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final AccountTransactionRepository transactions;
    private final PixKeyRepository pixKeys;
    private final TransactionTemplate tx;
    private final int customerCount;
    private final long seed;

    // Estado da geração (usado só durante run()).
    private Random random;
    private Instant now;
    private final Set<String> usedCpfs = new HashSet<>();
    private final Set<String> usedEmails = new HashSet<>();
    private final Set<String> usedAccountNumbers = new HashSet<>();
    private final List<Customer> newCustomers = new ArrayList<>();
    private final List<Account> newAccounts = new ArrayList<>();
    private final List<AccountTransaction> newTransactions = new ArrayList<>();
    private final List<PixKey> newPixKeys = new ArrayList<>();

    public DemoData(CustomerRepository customers, AccountRepository accounts,
                    AccountTransactionRepository transactions, PixKeyRepository pixKeys,
                    TransactionTemplate tx,
                    @Value("${bank.demo.customers:97}") int customerCount,
                    @Value("${bank.demo.seed:42}") long seed) {
        this.customers = customers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.pixKeys = pixKeys;
        this.tx = tx;
        this.customerCount = customerCount;
        this.seed = seed;
    }

    @Override
    public void run(String... args) {
        if (customers.count() > 0) {
            return;
        }
        tx.executeWithoutResult(status -> generate());
        log.info("Base fictícia criada: {} clientes, {} contas, {} lançamentos, {} chaves Pix",
                newCustomers.size(), newAccounts.size(), newTransactions.size(), newPixKeys.size());
    }

    private void generate() {
        random = new Random(seed);
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Contas fixas 1, 2 e 3: só o depósito inicial, para o saldo ser conhecido.
        Instant fixedOpening = now.minus(Duration.ofDays(60));
        fixedAccount("Ana Costa", "1500.00", fixedOpening);
        fixedAccount("Bruno Lima", "850.00", fixedOpening);
        fixedAccount("Carla Souza", "3200.00", fixedOpening);

        for (int i = 0; i < customerCount; i++) {
            generatedCustomer(i);
        }

        // Salva na ordem das dependências: cliente -> conta -> lançamentos e chaves.
        customers.saveAll(newCustomers);
        accounts.saveAll(newAccounts);
        transactions.saveAll(newTransactions);
        pixKeys.saveAll(newPixKeys);
    }

    private void fixedAccount(String name, String balance, Instant openedAt) {
        Customer customer = newCustomer(name, openedAt.minus(Duration.ofDays(3)));
        Account account = newAccount(customer, "0001", AccountType.CORRENTE, openedAt);
        record(account, TransactionType.DEPOSITO, new BigDecimal(balance), "Depósito inicial", openedAt);
        newPixKeys.add(new PixKey(PixKeyType.CPF, customer.getCpf(), account, openedAt));
    }

    private void generatedCustomer(int index) {
        Instant openedAt = now.minus(Duration.ofDays(30 + random.nextInt(700)));
        Customer customer = newCustomer(randomName(), openedAt.minus(Duration.ofDays(random.nextInt(10))));

        Account checking = newAccount(customer, pick(AGENCIES), AccountType.CORRENTE, openedAt);
        checkingHistory(checking, openedAt);
        addPixKeys(customer, checking, openedAt);

        if (random.nextDouble() < 0.40) {
            Instant savingsOpenedAt = openedAt.plus(Duration.ofDays(random.nextInt(20)));
            Account savings = newAccount(customer, checking.getAgency(), AccountType.POUPANCA, savingsOpenedAt);
            savingsHistory(savings, savingsOpenedAt);
        }
        // A cada 20 clientes, um tem a conta bloqueada e outro já teve uma conta encerrada.
        // Fixo (e não sorteado) para a base sempre ter esses casos para você testar.
        if (index % 20 == 7) {
            // Ex.: suspeita de fraude.
            checking.block();
        } else if (index % 20 == 13) {
            // Sacou tudo e fechou a conta.
            Instant oldOpening = customer.getCreatedAt();
            Account closed = newAccount(customer, checking.getAgency(), AccountType.CORRENTE, oldOpening);
            BigDecimal amount = money(200, 3000);
            record(closed, TransactionType.DEPOSITO, amount, "Depósito inicial", oldOpening);
            record(closed, TransactionType.SAQUE, amount, "Saque para encerramento da conta",
                    oldOpening.plus(Duration.ofDays(15)));
            closed.close();
        }
    }

    private void checkingHistory(Account account, Instant openedAt) {
        record(account, TransactionType.DEPOSITO, money(200, 5000), "Depósito inicial", openedAt);

        int events = 6 + random.nextInt(15);
        long window = Duration.between(openedAt, now.minus(Duration.ofHours(1))).toSeconds();
        Instant t = openedAt;
        for (int i = 0; i < events; i++) {
            t = t.plusSeconds(1 + (long) (window / events * (0.5 + random.nextDouble() * 0.5)));
            double kind = random.nextDouble();
            if (kind < 0.25) {
                record(account, TransactionType.DEPOSITO, money(1500, 9000), "Salário", t);
            } else if (kind < 0.40) {
                record(account, TransactionType.PIX_RECEBIDO, money(20, 800), "Pix recebido de " + randomName(), t);
            } else if (kind < 0.70) {
                debitIfPossible(account, TransactionType.PAGAMENTO, money(30, 900), pick(PAYMENTS), t);
            } else if (kind < 0.82) {
                debitIfPossible(account, TransactionType.SAQUE, money(50, 500), "Saque em caixa eletrônico", t);
            } else {
                debitIfPossible(account, TransactionType.PIX_ENVIADO, money(10, 600),
                        "Pix enviado para " + randomName(), t);
            }
        }
    }

    private void savingsHistory(Account account, Instant openedAt) {
        record(account, TransactionType.DEPOSITO, money(500, 20000), "Aplicação inicial", openedAt);
        // Rendimento mensal fictício de 0,5% sobre o saldo.
        for (Instant t = openedAt.plus(Duration.ofDays(30)); t.isBefore(now); t = t.plus(Duration.ofDays(30))) {
            BigDecimal yield = account.getBalance().multiply(new BigDecimal("0.005")).setScale(2, RoundingMode.HALF_EVEN);
            if (yield.signum() > 0) {
                record(account, TransactionType.RENDIMENTO, yield, "Rendimento da poupança", t);
            }
        }
    }

    private void addPixKeys(Customer customer, Account account, Instant createdAt) {
        newPixKeys.add(new PixKey(PixKeyType.CPF, customer.getCpf(), account, createdAt));
        if (random.nextDouble() < 0.5) {
            newPixKeys.add(new PixKey(PixKeyType.EMAIL, customer.getEmail(), account, createdAt));
        }
        if (random.nextDouble() < 0.3) {
            newPixKeys.add(new PixKey(PixKeyType.ALEATORIA, new UUID(random.nextLong(), random.nextLong()).toString(),
                    account, createdAt));
        }
    }

    private void debitIfPossible(Account account, TransactionType type, BigDecimal amount, String description, Instant at) {
        if (account.hasSufficientBalance(amount)) {
            record(account, type, amount, description, at);
        }
    }

    private void record(Account account, TransactionType type, BigDecimal amount, String description, Instant at) {
        newTransactions.add(AccountTransaction.apply(account, type, amount, description, null, at));
    }

    private Customer newCustomer(String name, Instant createdAt) {
        String cpf;
        do {
            cpf = Cpf.generate(random);
        } while (!usedCpfs.add(cpf));

        String base = slug(name);
        String email = base + "@gmail.com";
        for (int n = 2; !usedEmails.add(email); n++) {
            email = base + n + "@gmail.com";
        }

        LocalDate birthDate = LocalDate.of(1955, 1, 1).plusDays(random.nextInt(365 * 50));
        Customer customer = new Customer(name, cpf, email, birthDate, createdAt);
        newCustomers.add(customer);
        return customer;
    }

    private Account newAccount(Customer customer, String agency, AccountType type, Instant openedAt) {
        String number;
        do {
            int base = 10000 + random.nextInt(90000);
            number = base + "-" + checkDigit(base);
        } while (!usedAccountNumbers.add(agency + "/" + number));
        Account account = new Account(customer, agency, number, type, openedAt);
        newAccounts.add(account);
        return account;
    }

    private String randomName() {
        String first = pick(FIRST_NAMES);
        String last1 = pick(LAST_NAMES);
        String last2;
        do {
            last2 = pick(LAST_NAMES);
        } while (last2.equals(last1));
        return first + " " + last1 + " " + last2;
    }

    /** Valor aleatório entre min e max reais, com centavos. */
    private BigDecimal money(int min, int max) {
        long cents = (long) min * 100 + random.nextInt((max - min) * 100);
        return BigDecimal.valueOf(cents, 2);
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    /** "João Araújo" vira "joao.araujo". */
    private static String slug(String name) {
        String noAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", ".");
    }

    private static int checkDigit(int number) {
        int sum = 0;
        for (char c : String.valueOf(number).toCharArray()) {
            sum += c - '0';
        }
        return sum % 10;
    }
}
