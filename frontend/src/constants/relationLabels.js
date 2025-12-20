export const RELATION_LABELS = {
  FAMILY: "亲属",
  ALLY: "结盟",
  SUPPORT: "援助",
  RIVAL: "对抗",
  CONFLICT: "冲突",
  MENTOR: "师承",
  INFLUENCE: "影响",
  LOCATION_OF: "所在",
  PART_OF: "隶属",
  CAUSE: "因果",
  TEMPORAL: "时间",
  TRAVEL: "行旅",
  CUSTOM: "其他"
};

export const translateRelationLabel = (relationType) => {
  if (!relationType) return RELATION_LABELS.CUSTOM;
  return RELATION_LABELS[String(relationType).toUpperCase()] || RELATION_LABELS.CUSTOM;
};
