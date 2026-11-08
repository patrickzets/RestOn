package br.edu.reston.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Os mais recentes primeiro. Com texto vazio, traz todas as ações.
    Page<AuditLog> findByAcaoContainingIgnoreCaseOrderByEmDesc(String acao, Pageable paginacao);
}
