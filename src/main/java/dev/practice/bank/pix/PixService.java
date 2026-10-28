package dev.practice.bank.pix;

import dev.practice.bank.account.Account;
import dev.practice.bank.account.AccountNotFoundException;
import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.pix.PixDtos.CreatePixKeyRequest;
import dev.practice.bank.pix.PixDtos.PixKeyLookupResponse;
import dev.practice.bank.pix.PixDtos.PixKeyResponse;
import dev.practice.bank.pix.PixDtos.PixTransferRequest;
import dev.practice.bank.shared.Cpf;
import dev.practice.bank.transfer.SameAccountTransferException;
import dev.practice.bank.transfer.TransferChannel;
import dev.practice.bank.transfer.TransferResult;
import dev.practice.bank.transfer.TransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PixService {

    /** Limite de chaves por conta de pessoa física, como no Pix real. */
    public static final int MAX_KEYS_PER_ACCOUNT = 5;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern FORMATTED_DIGITS = Pattern.compile("^[\\d.\\-]+$");

    private final PixKeyRepository keys;
    private final AccountRepository accounts;
    private final TransferService transfers;

    public PixService(PixKeyRepository keys, AccountRepository accounts, TransferService transfers) {
        this.keys = keys;
        this.accounts = accounts;
        this.transfers = transfers;
    }

    @Transactional(readOnly = true)
    public PixKeyLookupResponse lookup(String rawKey) {
        return PixKeyLookupResponse.from(find(rawKey));
    }

    @Transactional(readOnly = true)
    public List<PixKeyResponse> listByAccount(Long accountId) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return keys.findByAccountIdOrderByIdAsc(accountId).stream().map(PixKeyResponse::from).toList();
    }

    @Transactional
    public PixKeyResponse register(Long accountId, CreatePixKeyRequest request) {
        Account account = accounts.findWithCustomerById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.ensureActive();

        String value = normalizeForRegistration(request.type(), request.value(), account);
        if (keys.existsByKeyValue(value)) {
            throw new PixKeyAlreadyExistsException();
        }
        if (keys.countByAccountId(accountId) >= MAX_KEYS_PER_ACCOUNT) {
            throw new PixKeyLimitException(MAX_KEYS_PER_ACCOUNT);
        }
        PixKey key = keys.save(new PixKey(request.type(), value, account, Instant.now()));
        return PixKeyResponse.from(key);
    }

    /** Resolve a chave para a conta de destino e reaproveita a mesma lógica da transferência. */
    @Transactional
    public TransferResult transfer(PixTransferRequest request, String idempotencyKey) {
        PixKey key = find(request.key());
        Long toAccountId = key.getAccount().getId();
        if (toAccountId.equals(request.fromAccountId())) {
            throw new SameAccountTransferException();
        }
        return transfers.execute(request.fromAccountId(), toAccountId, request.amount(),
                TransferChannel.PIX, request.description(), idempotencyKey);
    }

    private PixKey find(String rawKey) {
        return keys.findByKeyValue(normalizeForLookup(rawKey)).orElseThrow(PixKeyNotFoundException::new);
    }

    /** Na consulta não sabemos o tipo: CPF formatado vira só dígitos; o resto vai para minúsculas. */
    static String normalizeForLookup(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("A chave Pix é obrigatória");
        }
        String key = raw.trim();
        if (FORMATTED_DIGITS.matcher(key).matches() && Cpf.onlyDigits(key).length() == 11) {
            return Cpf.onlyDigits(key);
        }
        return key.toLowerCase(Locale.ROOT);
    }

    private static String normalizeForRegistration(PixKeyType type, String rawValue, Account account) {
        String ownerCpf = account.getCustomer().getCpf();
        return switch (type) {
            case CPF -> {
                // A chave CPF só pode ser o CPF do próprio titular.
                if (StringUtils.hasText(rawValue) && !Cpf.onlyDigits(rawValue).equals(ownerCpf)) {
                    throw new IllegalArgumentException("A chave CPF deve ser o CPF do titular da conta");
                }
                yield ownerCpf;
            }
            case EMAIL -> {
                if (!StringUtils.hasText(rawValue) || !EMAIL.matcher(rawValue.trim()).matches()) {
                    throw new IllegalArgumentException("E-mail inválido para chave Pix");
                }
                yield rawValue.trim().toLowerCase(Locale.ROOT);
            }
            case ALEATORIA -> {
                if (StringUtils.hasText(rawValue)) {
                    throw new IllegalArgumentException("A chave aleatória é gerada pelo banco; não envie valor");
                }
                yield UUID.randomUUID().toString();
            }
        };
    }
}
