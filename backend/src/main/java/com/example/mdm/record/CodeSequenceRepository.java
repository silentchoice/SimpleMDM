package com.example.mdm.record;

import java.time.LocalDate;

public interface CodeSequenceRepository {
  CodeRule findRule(long masterTypeId);
  void save(CodeRule rule);
  int allocate(long masterTypeId, LocalDate sequenceDate);
  boolean codeExists(long masterTypeId, String recordCode);
}
