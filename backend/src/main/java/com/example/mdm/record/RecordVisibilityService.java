package com.example.mdm.record;

import com.example.mdm.metadata.FieldDefinition;
import com.example.mdm.metadata.MetadataRepository;
import java.util.LinkedHashMap;
import java.util.HashMap;
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
    return filter(source, viewerDepartmentId, new HashMap<>(), new HashMap<>());
  }

  public List<RecordView> filterAll(List<RecordView> sources, Long viewerDepartmentId) {
    var masterFields = new HashMap<MasterScope, Set<String>>();
    var subFields = new HashMap<SubScope, Set<String>>();
    return sources.stream().map(source -> filter(source, viewerDepartmentId, masterFields,
        subFields)).toList();
  }

  private RecordView filter(RecordView source, Long viewerDepartmentId,
      Map<MasterScope, Set<String>> masterFields, Map<SubScope, Set<String>> subFields) {
    if (viewerDepartmentId != null && viewerDepartmentId == source.departmentId()) return source;

    Set<String> sharedMaster = masterFields.computeIfAbsent(
        new MasterScope(source.departmentId(), source.masterTypeId()), scope -> sharedCodes(
            metadata.findMasterFields(scope.departmentId(), scope.masterTypeId())));
    Map<String, Object> masterValues = visibleValues(source.masterValues(), sharedMaster);
    var children = source.children().stream().map(group -> {
      Set<String> shared = subFields.computeIfAbsent(
          new SubScope(source.departmentId(), group.subTypeId()), scope -> sharedCodes(
              metadata.findSubFields(scope.departmentId(), scope.subTypeId())));
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

  private record MasterScope(long departmentId, long masterTypeId) {}
  private record SubScope(long departmentId, long subTypeId) {}
}
