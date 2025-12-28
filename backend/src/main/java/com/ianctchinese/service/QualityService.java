package com.ianctchinese.service;

import com.ianctchinese.dto.EntityQualityIssue;
import com.ianctchinese.dto.RelationQualityIssue;
import com.ianctchinese.model.EntityAnnotation;
import com.ianctchinese.model.RelationAnnotation;
import com.ianctchinese.model.TextSection;
import com.ianctchinese.repository.EntityAnnotationRepository;
import com.ianctchinese.repository.RelationAnnotationRepository;
import com.ianctchinese.repository.TextSectionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QualityService {

  private final EntityAnnotationRepository entityAnnotationRepository;
  private final RelationAnnotationRepository relationAnnotationRepository;
  private final TextSectionRepository textSectionRepository;

  public List<EntityQualityIssue> checkEntityIssues(Long textId) {
    List<EntityAnnotation> entities = entityAnnotationRepository.findByTextDocumentId(textId);
    Map<Long, TextSection> sectionMap = new HashMap<>();
    textSectionRepository.findByTextDocumentId(textId)
        .forEach(section -> sectionMap.put(section.getId(), section));

    List<EntityQualityIssue> issues = new ArrayList<>();
    Map<String, List<EntityAnnotation>> duplicateMap = new HashMap<>();

    for (EntityAnnotation entity : entities) {
      Integer start = entity.getStartOffset();
      Integer end = entity.getEndOffset();
      TextSection section = entity.getSection();
      Long sectionId = section != null ? section.getId() : null;
      Integer sectionIndex = section != null ? section.getSequenceIndex() : null;
      String category = entity.getCategory() != null ? entity.getCategory().name() : null;

      if (start != null && end != null && start >= end) {
        issues.add(buildEntityIssue(
            textId,
            entity,
            sectionId,
            sectionIndex,
            "INVALID_SPAN",
            "start_offset >= end_offset"
        ));
      }

      if (sectionId != null && start != null && end != null) {
        TextSection sectionData = sectionMap.get(sectionId);
        if (sectionData != null && sectionData.getOriginalText() != null) {
          int length = sectionData.getOriginalText().length();
          if (start < 0 || end > length || start > length) {
            issues.add(buildEntityIssue(
                textId,
                entity,
                sectionId,
                sectionIndex,
                "OUT_OF_RANGE",
                "offset exceeds section length"
            ));
          }
        }
      }

      String key = String.format(
          "%s|%s|%s|%s|%s",
          sectionId,
          start,
          end,
          entity.getLabel(),
          category
      );
      duplicateMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entity);
    }

    Set<Long> duplicateEntityIds = new HashSet<>();
    for (List<EntityAnnotation> group : duplicateMap.values()) {
      if (group.size() < 2) {
        continue;
      }
      for (EntityAnnotation entity : group) {
        if (duplicateEntityIds.add(entity.getId())) {
          TextSection section = entity.getSection();
          Long sectionId = section != null ? section.getId() : null;
          Integer sectionIndex = section != null ? section.getSequenceIndex() : null;
          issues.add(buildEntityIssue(
              textId,
              entity,
              sectionId,
              sectionIndex,
              "DUPLICATE",
              "duplicate annotation in same section"
          ));
        }
      }
    }

    return issues;
  }

  public List<RelationQualityIssue> checkRelationIssues(Long textId) {
    List<RelationAnnotation> relations = relationAnnotationRepository.findByTextDocumentId(textId);
    List<RelationQualityIssue> issues = new ArrayList<>();

    for (RelationAnnotation relation : relations) {
      EntityAnnotation source = relation.getSource();
      EntityAnnotation target = relation.getTarget();
      Long sourceId = source != null ? source.getId() : null;
      Long targetId = target != null ? target.getId() : null;
      Long sourceSectionId = source != null && source.getSection() != null ? source.getSection().getId() : null;
      Long targetSectionId = target != null && target.getSection() != null ? target.getSection().getId() : null;
      String relationType = relation.getRelationType() != null ? relation.getRelationType().name() : null;
      String evidence = relation.getEvidence();

      if (sourceId == null || targetId == null) {
        issues.add(buildRelationIssue(
            textId,
            relation,
            sourceId,
            targetId,
            sourceSectionId,
            targetSectionId,
            relationType,
            evidence,
            "BROKEN_REFERENCE",
            "source or target entity is missing"
        ));
      }

      if (sourceId != null && targetId != null && sourceId.equals(targetId)) {
        issues.add(buildRelationIssue(
            textId,
            relation,
            sourceId,
            targetId,
            sourceSectionId,
            targetSectionId,
            relationType,
            evidence,
            "SELF_LOOP",
            "source and target are the same entity"
        ));
      }

      if (evidence == null || evidence.isBlank()) {
        issues.add(buildRelationIssue(
            textId,
            relation,
            sourceId,
            targetId,
            sourceSectionId,
            targetSectionId,
            relationType,
            evidence,
            "EMPTY_EVIDENCE",
            "evidence is empty"
        ));
      }
    }

    return issues;
  }

  private EntityQualityIssue buildEntityIssue(
      Long textId,
      EntityAnnotation entity,
      Long sectionId,
      Integer sectionIndex,
      String type,
      String message) {
    String category = entity.getCategory() != null ? entity.getCategory().name() : null;
    return EntityQualityIssue.builder()
        .entityId(entity.getId())
        .textId(textId)
        .sectionId(sectionId)
        .sectionIndex(sectionIndex)
        .startOffset(entity.getStartOffset())
        .endOffset(entity.getEndOffset())
        .label(entity.getLabel())
        .category(category)
        .issueType(type)
        .message(message)
        .build();
  }

  private RelationQualityIssue buildRelationIssue(
      Long textId,
      RelationAnnotation relation,
      Long sourceId,
      Long targetId,
      Long sourceSectionId,
      Long targetSectionId,
      String relationType,
      String evidence,
      String type,
      String message) {
    return RelationQualityIssue.builder()
        .relationId(relation.getId())
        .textId(textId)
        .sourceId(sourceId)
        .targetId(targetId)
        .sourceSectionId(sourceSectionId)
        .targetSectionId(targetSectionId)
        .relationType(relationType)
        .evidence(evidence)
        .issueType(type)
        .message(message)
        .build();
  }
}
