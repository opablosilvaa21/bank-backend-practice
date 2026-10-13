package dev.practice.bank.customer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Clientes", description = "Consulta de clientes fictícios")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista clientes com paginação e busca opcional por nome")
    public Page<CustomerResponse> search(
            @Parameter(description = "Parte do nome, sem diferenciar maiúsculas") @RequestParam(required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(name, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um cliente e suas contas")
    @ApiResponse(responseCode = "200", description = "Cliente encontrado")
    @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    public CustomerDetailResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
