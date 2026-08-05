package com.example.mdm.record;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCodeSequenceRepository implements CodeSequenceRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcCodeSequenceRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override public CodeRule findRule(long masterTypeId) {
    var rules = jdbc.query("SELECT master_type_id,pattern,sequence_width FROM master_type_code_rules "
            + "WHERE master_type_id=:masterTypeId", Map.of("masterTypeId", masterTypeId),
        (result, row) -> new CodeRule(result.getLong("master_type_id"), result.getString("pattern"),
            result.getInt("sequence_width")));
    return rules.isEmpty() ? null : rules.get(0);
  }

  @Override public void save(CodeRule rule) {
    jdbc.update("INSERT INTO master_type_code_rules(master_type_id,pattern,sequence_width) "
            + "VALUES(:masterTypeId,:pattern,:sequenceWidth) ON DUPLICATE KEY UPDATE "
            + "pattern=VALUES(pattern),sequence_width=VALUES(sequence_width)",
        Map.of("masterTypeId", rule.masterTypeId(), "pattern", rule.pattern(),
            "sequenceWidth", rule.sequenceWidth()));
  }

  @Override public int allocate(long masterTypeId, LocalDate sequenceDate) {
    var parameters = Map.of("masterTypeId", masterTypeId, "sequenceDate", sequenceDate);
    jdbc.update("INSERT INTO code_sequences(master_type_id,sequence_date,next_value) "
            + "VALUES(:masterTypeId,:sequenceDate,2) ON DUPLICATE KEY UPDATE next_value=next_value+1",
        parameters);
    Integer allocated = jdbc.queryForObject("SELECT next_value-1 FROM code_sequences "
        + "WHERE master_type_id=:masterTypeId AND sequence_date=:sequenceDate FOR UPDATE", parameters,
        Integer.class);
    if (allocated == null) throw new IllegalStateException("Code sequence allocation failed");
    return allocated;
  }
}
