package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecordSnapshotCodecTest {
  private final ObjectMapper json = new ObjectMapper();
  private final RecordSnapshotCodec codec = new RecordSnapshotCodec(json);

  @Test void encodesTheVersionedPortableSnapshotEnvelopeInDeterministicChildOrder() throws Exception {
    var values = new LinkedHashMap<String, Object>();
    values.put("name", "North Supplier");
    values.put("rating", 4);
    var draft = new RecordDraft(17L, 81L, 9L, 7L, "CUS-20260805-0001",
        RecordAction.UPDATE, 3L, values, List.of(
            new RecordDraft.ChildRows(31L, List.of(
                new RecordDraft.ChildRow(102L, 2, Map.of("contact", "Wang")),
                new RecordDraft.ChildRow(101L, 0, Map.of("contact", "Li")))),
            new RecordDraft.ChildRows(45L, List.of(
                new RecordDraft.ChildRow(null, 1, Map.of("city", "Shanghai"))))),
        RecordStatus.DRAFT, 12L, null);

    String snapshot = codec.encode(draft);
    var tree = json.readTree(snapshot);

    assertThat(tree.fieldNames()).toIterable().containsExactly("schemaVersion", "departmentId",
        "masterTypeId", "recordId", "recordCode", "action", "baseVersion", "masterValues",
        "children");
    assertThat(tree.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(tree.get("departmentId").asLong()).isEqualTo(7L);
    assertThat(tree.get("recordId").asLong()).isEqualTo(81L);
    assertThat(tree.get("action").asText()).isEqualTo("UPDATE");
    assertThat(tree.get("children").get(0).get("subTypeId").asLong()).isEqualTo(31L);
    assertThat(tree.get("children").get(0).get("rows").get(0).get("rowOrder").asInt())
        .isZero();
    assertThat(tree.get("children").get(0).get("rows").get(1).get("rowOrder").asInt())
        .isEqualTo(2);
    assertThat(snapshot).doesNotContain("java.io", "RecordDraft[");
  }

  @Test void decodesThePortableSnapshotWithoutDraftOnlyState() {
    String snapshot = """
        {"schemaVersion":1,"departmentId":7,"masterTypeId":9,"recordId":81,
         "recordCode":"CUS-20260805-0001","action":"UPDATE","baseVersion":3,
         "masterValues":{"name":"North Supplier"},
         "children":[{"subTypeId":31,"rows":[{"recordId":101,"rowOrder":0,
         "values":{"contact":"Li"}}]}]}
        """;

    var decoded = codec.decode(snapshot);

    assertThat(decoded.schemaVersion()).isEqualTo(1);
    assertThat(decoded.masterValues()).containsEntry("name", "North Supplier");
    assertThat(decoded.children()).containsExactly(
        new RecordSnapshotCodec.SnapshotChildRows(31L, List.of(
            new RecordSnapshotCodec.SnapshotChildRow(101L, 0, Map.of("contact", "Li")))));
  }
}
