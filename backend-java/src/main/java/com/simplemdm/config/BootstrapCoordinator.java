package com.simplemdm.config;

import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.repository.system.SystemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

@Component
public class BootstrapCoordinator {
    private final SystemRepository systems;
    private final TransactionTemplate required;
    private final TransactionTemplate requiresNew;

    public BootstrapCoordinator(SystemRepository systems, PlatformTransactionManager transactions) {
        this.systems = systems;
        required = new TransactionTemplate(transactions);
        requiresNew = new TransactionTemplate(transactions);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void withLockedSystem(String code, String name, Consumer<SystemEntity> seed) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            SystemEntity existing = systems.findByCode(code)
                .orElseGet(() -> systems.saveAndFlush(SystemEntity.create(code, name)));
            seed.accept(systems.findForUpdateByCode(existing.getCode()).orElseThrow());
            return;
        }
        ensureSystem(code, name);
        required.executeWithoutResult(status -> seed.accept(
            systems.findForUpdateByCode(code).orElseThrow(
                () -> new IllegalStateException("Bootstrap system disappeared: " + code))));
    }

    private void ensureSystem(String code, String name) {
        try {
            requiresNew.executeWithoutResult(status -> {
                if (systems.findByCode(code).isEmpty())
                    systems.saveAndFlush(SystemEntity.create(code, name));
            });
        } catch (DataIntegrityViolationException concurrentInsert) {
            // The stable-code unique key selected the winner. The failed transaction is already closed.
            Boolean present = requiresNew.execute(status -> systems.findByCode(code).isPresent());
            if (!Boolean.TRUE.equals(present)) throw concurrentInsert;
        }
    }
}
