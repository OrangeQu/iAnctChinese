package com.ianctchinese.dto;

import com.ianctchinese.model.EntityAnnotation;
import com.ianctchinese.model.RelationAnnotation;
import com.ianctchinese.model.TextDocument;
import com.ianctchinese.model.TextSection;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextExportBundle {

  private TextDocument text;
  private List<TextSection> sections;
  private List<EntityAnnotation> entities;
  private List<RelationAnnotation> relations;
}
