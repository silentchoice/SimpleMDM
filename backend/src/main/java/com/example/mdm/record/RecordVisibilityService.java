package com.example.mdm.record;

import com.example.mdm.metadata.FieldDefinition;
import com.example.mdm.metadata.MetadataRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RecordVisibilityService {
  private final MetadataRepository metadata;

  public RecordVisibilityService(MetadataRepository metadata) {
    this.metadata = metadata;
  }

  public RecordView filter(RecordView source, Long viewerDepartmentId) {
    if (viewerDepartmentId != null && viewerDepartmentId == source.departmentId()) return source;

    Set<String> sharedMaster = sharedCodes(
        metadata.findMasterFields(source.departmentId(), source.masterTypeId()));
    Map<String, Object> masterValues = visibleValues(source.masterValues(), sharedMaster);
    var children = source.children().stream().map(group -> {
      Set<String> shared = sharedCodes(
          metadata.findSubFields(source.departmentId(), group.subTypeId()));
      var rows = group.rows().stream().map(row -> new RecordView.ChildRow(row.id(),
              row.rowOrder(), visibleValues(row.values(), shared)))
          .filter(row -> !row.values().isEmpty()).toList();
      return new RecordView.ChildRows(group.subTypeId(), rows);
    }).filter(group -> !group.rows().isEmpty()).toList();
    return new RecordView(source.id(), source.masterTypeId(), source.departmentId(),
        source.recordCode(), masterValues, children, source.version(), source.status());
  }

  private Set<String> sharedCodes(List<FieldDefinition> definitions) {
    return definitions.stream().filter(FieldDefinition::shared).map(FieldDefinition::code)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Map<String, Object> visibleValues(Map<String, Object> values, Set<String> sharedCodes) {
    var visible = new LinkedHashMap<String, Object>();
    values.forEach((code, value) -> {
      if (sharedCodes.contains(code)) visible.put(code, value);
    });
    return Collections.unmodifiableMap(visible);
  }
}
