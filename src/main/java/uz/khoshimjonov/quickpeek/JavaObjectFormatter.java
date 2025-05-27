package uz.khoshimjonov.quickpeek;

import java.util.ArrayList;
import java.util.List;

public class JavaObjectFormatter {

    public String formatJavaObject(String objectString) {
        try {
            objectString = objectString.trim();
            if (objectString.isEmpty()) {
                return null;
            }

            if (!isJavaObjectString(objectString)) {
                return null;
            }

            return formatJavaObjectManually(objectString);

        } catch (Exception e) {
            System.out.println("Java object formatting failed: " + e.getMessage());
            return objectString;
        }
    }

    public boolean isJavaObjectString(String str) {
        return str.matches("^[A-Za-z_$][A-Za-z0-9_$.]*\\s*[({][^})].*[)}]$");
    }

    private String formatJavaObjectManually(String objectString) {
        try {
            int openBracket = -1;
            char bracketType = 0;

            for (int i = 0; i < objectString.length(); i++) {
                char c = objectString.charAt(i);
                if (c == '(' || c == '{') {
                    openBracket = i;
                    bracketType = c;
                    break;
                }
            }

            if (openBracket == -1) {
                return objectString;
            }

            String className = objectString.substring(0, openBracket).trim();
            String content = objectString.substring(openBracket + 1);

            char closingBracket = (bracketType == '(') ? ')' : '}';
            if (content.endsWith(String.valueOf(closingBracket))) {
                content = content.substring(0, content.length() - 1);
            }

            StringBuilder formatted = new StringBuilder();
            formatted.append(className).append(bracketType).append("\n");

            List<String> fields = parseObjectFieldsAdvanced(content);

            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i).trim();
                if (!field.isEmpty()) {
                    String formattedField = formatField(field);
                    formatted.append("  ").append(formattedField);
                    if (i < fields.size() - 1) {
                        formatted.append(",");
                    }
                    formatted.append("\n");
                }
            }

            formatted.append(closingBracket);
            return formatted.toString();

        } catch (Exception e) {
            System.out.println("Error in manual Java object formatting: " + e.getMessage());
            return objectString;
        }
    }

    private List<String> parseObjectFieldsAdvanced(String content) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        int parenLevel = 0;
        int braceLevel = 0;
        int bracketLevel = 0;
        boolean inString = false;
        boolean escaped = false;
        char stringDelimiter = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if ((c == '"' || c == '\'') && !escaped) {
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == stringDelimiter) {
                    inString = false;
                    stringDelimiter = 0;
                }
                currentField.append(c);
                continue;
            }

            if (inString) {
                currentField.append(c);
                escaped = (c == '\\' && !escaped);
                continue;
            }

            escaped = false;

            switch (c) {
                case '(':
                    parenLevel++;
                    currentField.append(c);
                    break;
                case ')':
                    parenLevel--;
                    currentField.append(c);
                    break;
                case '{':
                    braceLevel++;
                    currentField.append(c);
                    break;
                case '}':
                    braceLevel--;
                    currentField.append(c);
                    break;
                case '[':
                    bracketLevel++;
                    currentField.append(c);
                    break;
                case ']':
                    bracketLevel--;
                    currentField.append(c);
                    break;
                case ',':
                    if (parenLevel == 0 && braceLevel == 0 && bracketLevel == 0) {
                        fields.add(currentField.toString());
                        currentField = new StringBuilder();
                    } else {
                        currentField.append(c);
                    }
                    break;
                default:
                    currentField.append(c);
                    break;
            }
        }

        if (!currentField.isEmpty()) {
            fields.add(currentField.toString());
        }

        return fields;
    }

    private String formatField(String field) {
        try {
            int equalsIndex = field.indexOf('=');
            if (equalsIndex == -1) {
                return field;
            }

            String fieldName = field.substring(0, equalsIndex).trim();
            String fieldValue = field.substring(equalsIndex + 1).trim();

            String formattedValue = formatFieldValue(fieldValue);

            return fieldName + " = " + formattedValue;

        } catch (Exception e) {
            return field;
        }
    }

    private String formatFieldValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "null";
        }

        value = value.trim();

        if (value.equals("null")) {
            return "null";
        } else if (value.startsWith("'") && value.endsWith("'")) {
            return value;
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            return value;
        } else if (value.matches("^-?\\d+$")) {
            return value;
        } else if (value.matches("^-?\\d*\\.\\d+$")) {
            return value;
        } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return value.toLowerCase();
        } else if (value.startsWith("[") && value.endsWith("]")) {
            return formatArrayValue(value);
        } else if (isJavaObjectString(value)) {
            String formatted = formatJavaObject(value);
            if (formatted != null && !formatted.equals(value)) {
                return indentNestedObject(formatted);
            }
            return formatted != null ? formatted : value;
        } else {
            if (containsSpaces(value) && !value.startsWith("'") && !value.startsWith("\"")) {
                return "\"" + value + "\"";
            }
            return value;
        }
    }

    private boolean containsSpaces(String value) {
        return value.contains(" ") && !value.matches("^[A-Z_]+$");
    }

    private String indentNestedObject(String nestedObject) {
        String[] lines = nestedObject.split("\n");
        StringBuilder indented = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                indented.append(lines[i]);
            } else {
                indented.append("  ").append(lines[i]);
            }
            if (i < lines.length - 1) {
                indented.append("\n");
            }
        }

        return indented.toString();
    }

    private String formatArrayValue(String arrayValue) {
        try {
            String content = arrayValue.substring(1, arrayValue.length() - 1).trim();
            if (content.isEmpty()) {
                return "[]";
            }

            String[] elements = content.split(",");
            if (elements.length <= 3) {
                StringBuilder formatted = new StringBuilder("[");
                for (int i = 0; i < elements.length; i++) {
                    if (i > 0) formatted.append(", ");
                    formatted.append(elements[i].trim());
                }
                formatted.append("]");
                return formatted.toString();
            } else {
                StringBuilder formatted = new StringBuilder("[\n");
                for (int i = 0; i < elements.length; i++) {
                    formatted.append("    ").append(elements[i].trim());
                    if (i < elements.length - 1) {
                        formatted.append(",");
                    }
                    formatted.append("\n");
                }
                formatted.append("  ]");
                return formatted.toString();
            }
        } catch (Exception e) {
            return arrayValue;
        }
    }
}
