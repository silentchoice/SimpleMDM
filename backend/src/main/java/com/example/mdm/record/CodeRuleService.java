package com.example.mdm.record;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeRuleService {
  private final CodeSequenceRepository repository;
  private final CodeRuleParser parser;
  private final AuthorizationService authorization;
  private final Clock clock;

  public CodeRuleService(CodeSequenceRepository repository, CodeRuleParser parser,
      AuthorizationService authorization, Clock clock) {
    this.repository = repository;
    this.parser = parser;
    this.authorization = authorization;
    this.clock = clock;
  }

  @Autowired
  CodeRuleService(CodeSequenceRepository repository, CodeRuleParser parser,
      AuthorizationService authorization, ObjectProvider<Clock> clocks) {
    this(repository, parser, authorization, clocks.getIfAvailable(Clock::systemUTC));
  }

  public CodeRule find(long masterTypeId) {
    authorization.requireRole(Role.SUPER_ADMIN);
    CodeRule rule = repository.findRule(masterTypeId);
    if (rule == null) throw BusinessException.notFound("Code rule");
    return rule;
  }

  public CodeRule save(long masterTypeId, String pattern) {
    authorization.requireRole(Role.SUPER_ADMIN);
    CodeRule parsed = parser.parse(pattern);
    CodeRule rule = new CodeRule(masterTypeId, parsed.pattern(), parsed.sequenceWidth());
    repository.save(rule);
    return rule;
  }

  @Transactional
  public String allocate(long masterTypeId, LocalDate sequenceDate) {
    CodeRule rule = repository.findRule(masterTypeId);
    if (rule == null) throw BusinessException.notFound("Code rule");
    return rule.render(sequenceDate, repository.allocate(masterTypeId, sequenceDate));
  }

  public String preview(CodeRule rule) {
    return rule.render(LocalDate.now(clock), 1);
  }
}
