export const ENTITY_LABELS = {
  PERSON: "人物",
  LOCATION: "地点",
  EVENT: "事件",
  ORGANIZATION: "组织",
  OBJECT: "器物",
  CUSTOM: "自定义"
};

export const translateEntityLabel = (category) => {
  if (!category) return "实体";
  return ENTITY_LABELS[String(category).toUpperCase()] || category;
};
