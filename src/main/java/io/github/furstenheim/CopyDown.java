package io.github.furstenheim;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main class of the package
 */
public class CopyDown {
    public CopyDown() {
        this.options = OptionsBuilder.anOptions()
                .build();
        setUp();
    }

    public CopyDown(Options options) {
        this.options = options;
        setUp();
    }

    /**
     * Accepts an HTML string and converts it to Markdown
     * <p>
     * Note, if LinkStyle is chosen to be REFERENCED, the method is not thread safe.
     *
     * @param input HTML to be converted
     * @return markdown text
     */
    public String convert(String input) {
        references = new ArrayList<>();
        CopyNode copyRootNode = new CopyNode(input);
        String result = process(copyRootNode);
        return postProcess(result);
    }

    private Rules rules;
    private final Options options;
    private List<String> references = null;

    private void setUp() {
        rules = new Rules();
    }

    private static class Escape {
        String pattern;
        String replace;

        public Escape(String pattern, String replace) {
            this.pattern = pattern;
            this.replace = replace;
        }
    }

    private final List<Escape> escapes = Arrays.asList(new Escape("\\\\", "\\\\\\\\"), new Escape("\\*", "\\\\*"),
            new Escape("^-", "\\\\-"), new Escape("^\\+ ", "\\\\+ "), new Escape("^(=+)", "\\\\$1"),
            new Escape("^(#{1,6}) ", "\\\\$1 "), new Escape("`", "\\\\`"), new Escape("^~~~", "\\\\~~~"),
            new Escape("\\[", "\\\\["), new Escape("\\]", "\\\\]"), new Escape("^>", "\\\\>"), new Escape("_", "\\\\_"),
            new Escape("^(\\d+)\\. ", "$1\\\\. "));

    private String postProcess(String output) {
        for (Rule rule : rules.rules) {
            if (rule.getAppend() != null) {
                output = join(output, rule.getAppend()
                        .get());
            }
        }
        return output.replaceAll("^[\\t\\n\\r]+", "")
                .replaceAll("[\\t\\r\\n\\s]+$", "");
    }

    private String process(CopyNode node) {
        String result = "";
        for (Node child : node.element.childNodes()) {
            CopyNode copyNodeChild = new CopyNode(child, node);
            String replacement = "";
            if (NodeUtils.isNodeType3(child)) {
                // TODO it should be child.nodeValue
                replacement = copyNodeChild.isCode() ? ((TextNode) child).text() : escape(((TextNode) child).text());
            } else if (NodeUtils.isNodeType1(child)) {
                replacement = replacementForNode(copyNodeChild);
            }
            result = join(result, replacement);
        }
        return result;
    }

    private String replacementForNode(CopyNode node) {
        Rule rule = rules.findRule(node.element);
        String content = process(node);
        CopyNode.FlankingWhiteSpaces flankingWhiteSpaces = node.flankingWhitespace();
        if (!flankingWhiteSpaces.getLeading()
                .isEmpty() || !flankingWhiteSpaces.getTrailing()
                .isEmpty()) {
            content = content.trim();
        }
        return flankingWhiteSpaces.getLeading() + rule.getReplacement()
                .apply(content, node.element) + flankingWhiteSpaces.getTrailing();
    }

    private static final Pattern leadingNewLinePattern = Pattern.compile("^(\n*)");
    private static final Pattern trailingNewLinePattern = Pattern.compile("(\n*)$");

    private String join(String string1, String string2) {
        Matcher trailingMatcher = trailingNewLinePattern.matcher(string1);
        boolean hasTrailingMatch = trailingMatcher.find();

        Matcher leadingMatcher = leadingNewLinePattern.matcher(string2);
        boolean hasLeadingMatch = leadingMatcher.find();

        int trailingLength = hasTrailingMatch ? trailingMatcher.group().length() : 0;
        int leadingLength = hasLeadingMatch ? leadingMatcher.group().length() : 0;

        int nNewLines = Integer.min(2, Integer.max(leadingLength, trailingLength));
        String newLineJoin = String.join("", Collections.nCopies(nNewLines, "\n"));

        return trailingMatcher.replaceAll("") + newLineJoin + leadingMatcher.replaceAll("");
    }


    private String escape(String string) {
        for (Escape escape : escapes) {
            string = string.replaceAll(escape.pattern, escape.replace);
        }
        return string;
    }

    class Rules {
        private final List<Rule> rules;

        public Rules() {
            this.rules = new ArrayList<>();

            addRule("blankReplacement", new Rule(CopyNode::isBlank,
                    (content, element) -> CopyNode.isBlock(element) ? "\n\n" : ""));
            addRule("paragraph", new Rule("p", (content, element) -> "\n\n" + content + "\n\n"));
            addRule("br", new Rule("br", (content, element) -> options.br + "\n"));
            addRule("heading", new Rule(new String[] { "h1", "h2", "h3", "h4", "h5", "h6" }, (content, element) -> {
                int hLevel = Integer.parseInt(element.nodeName()
                        .substring(1, 2));
                if (options.headingStyle == HeadingStyle.SETEXT && hLevel < 3) {
                    String underline = String.join("", Collections.nCopies(content.length(), hLevel == 1 ? "=" : "-"));
                    return "\n\n" + content + "\n" + underline + "\n\n";
                } else {
                    return "\n\n" + String.join("", Collections.nCopies(hLevel, "#")) + " " + content + "\n\n";
                }
            }));
            addRule("blockquote", new Rule("blockquote", (content, element) -> {
                content = content.replaceAll("^\n+|\n+$", "");
                content = content.replaceAll("(?m)^", "> ");
                return "\n\n" + content + "\n\n";
            }));
            addRule("table", new Rule("table", (content, element) -> convertTable((Element) element)));
            addRule("list", new Rule(new String[] { "ul", "ol" }, (content, element) -> {
                Element parent = (Element) element.parentNode();
                if (Objects.requireNonNull(parent)
                        .nodeName()
                        .equals("li") && parent.child(parent.childrenSize() - 1) == element) {
                    return "\n" + content;
                } else {
                    return "\n\n" + content + "\n\n";
                }
            }));
            addRule("listItem", new Rule("li", (content, element) -> {
                content = content.replaceAll("^\n+", "") // remove leading new lines
                        .replaceAll("\n+$", "\n") // remove trailing new lines with just a single one
                        .replaceAll("(?m)\n", "\n    "); // indent
                String prefix = options.bulletListMaker + "   ";
                Element parent = (Element) element.parentNode();
                if (Objects.requireNonNull(parent)
                        .nodeName()
                        .equals("ol")) {
                    String start = parent.attr("start");
                    List<Element> children = parent.children();

                    int index = -1;
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i) == element) {
                            index = i;
                            break;
                        }
                    }
                    int parsedStart = 1;
                    if (!start.isEmpty()) {
                        try {
                            parsedStart = Integer.parseInt(start);
                        } catch (NumberFormatException e) {
                            throw new NumberFormatException("Unable to parse " + start + " as an integer");
                        }
                    }
                    prefix = parsedStart + index + ".  ";
                }
                return prefix + content + (element.nextSibling() != null && !Pattern.compile("\n$")
                        .matcher(content)
                        .find() ? "\n" : "");
            }));
            addRule("indentedCodeBlock", new Rule((element) -> options.codeBlockStyle == CodeBlockStyle.INDENTED && element.nodeName()
                    .equals("pre") && element.childNodeSize() > 0 && element.childNode(0)
                    .nodeName()
                    .equals("code"), (content, element) -> {
                // TODO check textContent
                return "\n\n    " + ((Element) element.childNode(0)).wholeText()
                        .replaceAll("\n", "\n    ");
            }));
            addRule("fencedCodeBock", new Rule((element) -> options.codeBlockStyle == CodeBlockStyle.FENCED && element.nodeName()
                    .equals("pre") && element.childNodeSize() > 0 && element.childNode(0)
                    .nodeName()
                    .equals("code"), (content, element) -> {
                String childClass = element.childNode(0)
                        .attr("class");
                Matcher languageMatcher = Pattern.compile("language-(\\S+)")
                        .matcher(childClass);
                String language = "";
                if (languageMatcher.find()) {
                    language = languageMatcher.group(1);
                }

                String code;
                if (element.childNode(0) instanceof Element) {
                    code = ((Element) element.childNode(0)).wholeText();
                } else {
                    code = element.childNode(0)
                            .outerHtml();
                }

                String fenceChar = options.fence.substring(0, 1);
                int fenceSize = 3;
                Matcher fenceMatcher = Pattern.compile("(?m)^(" + fenceChar + "{3,})")
                        .matcher(content);
                while (fenceMatcher.find()) {
                    String group = fenceMatcher.group(1);
                    fenceSize = Math.max(group.length() + 1, fenceSize);
                }
                String fence = String.join("", Collections.nCopies(fenceSize, fenceChar));
                if (!code.isEmpty() && code.charAt(code.length() - 1) == '\n') {
                    code = code.substring(0, code.length() - 1);
                }
                return ("\n\n" + fence + language + "\n" + code + "\n" + fence + "\n\n");
            }));

            addRule("horizontalRule", new Rule("hr", (content, element) -> "\n\n" + options.hr + "\n\n"));
            addRule("inlineLink", new Rule((element) -> options.linkStyle == LinkStyle.INLINED && element.nodeName()
                    .equals("a") && !element.attr("href")
                    .isEmpty(), (content, element) -> {
                String href = element.attr("href");
                String title = cleanAttribute(element.attr("title"));
                if (!title.isEmpty()) {
                    title = " \"" + title + "\"";
                }
                return "[" + content + "](" + href + title + ")";
            }));
            addRule("referenceLink", new Rule((element) -> options.linkStyle == LinkStyle.REFERENCED && element.nodeName()
                    .equals("a") && !element.attr("href")
                    .isEmpty(), (content, element) -> {
                String href = element.attr("href");
                String title = cleanAttribute(element.attr("title"));
                if (!title.isEmpty()) {
                    title = " \"" + title + "\"";
                }
                String replacement;
                String reference;
                switch (options.linkReferenceStyle) {
                    case COLLAPSED:
                        replacement = "[" + content + "][]";
                        reference = "[" + content + "]: " + href + title;
                        break;
                    case SHORTCUT:
                        replacement = "[" + content + "]";
                        reference = "[" + content + "]: " + href + title;
                        break;
                    case DEFAULT:
                    default:
                        int id = references.size() + 1;
                        replacement = "[" + content + "][" + id + "]";
                        reference = "[" + id + "]: " + href + title;
                }
                references.add(reference);
                return replacement;
            }, () -> {
                String referenceString = "";
                if (!references.isEmpty()) {
                    referenceString = "\n\n" + String.join("\n", references) + "\n\n";
                }
                return referenceString;
            }));
            addRule("emphasis", new Rule(new String[] { "em", "i" }, (content, element) -> {
                if (content.trim()
                        .isEmpty()) {
                    return "";
                }
                return options.emDelimiter + content + options.emDelimiter;
            }));
            addRule("strong", new Rule(new String[] { "strong", "b" }, (content, element) -> {
                if (content.trim()
                        .isEmpty()) {
                    return "";
                }
                return options.strongDelimiter + content + options.strongDelimiter;
            }));
            addRule("code", new Rule((element) -> {
                boolean hasSiblings = element.previousSibling() != null || element.nextSibling() != null;
                boolean isCodeBlock = Objects.requireNonNull(element.parentNode())
                        .nodeName()
                        .equals("pre") && !hasSiblings;
                return element.nodeName()
                        .equals("code") && !isCodeBlock;
            }, (content, element) -> {
                if (content.trim()
                        .isEmpty()) {
                    return "";
                }
                String delimiter = "`";
                String leadingSpace = "";
                String trailingSpace = "";
                Pattern pattern = Pattern.compile("(?m)(`)+");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    if (Pattern.compile("^`")
                            .matcher(content)
                            .find()) {
                        leadingSpace = " ";
                    }
                    if (Pattern.compile("`$")
                            .matcher(content)
                            .find()) {
                        trailingSpace = " ";
                    }
                    int counter = 1;
                    do {
                        if (delimiter.equals(matcher.group())) {
                            counter++;
                        }
                    } while (matcher.find());
                    delimiter = String.join("", Collections.nCopies(counter, "`"));
                }
                return delimiter + leadingSpace + content + trailingSpace + delimiter;
            }));
            addRule("img", new Rule("img", (content, element) -> {
                String alt = cleanAttribute(element.attr("alt"));
                String src = element.attr("src");
                if (src.isEmpty()) {
                    return "";
                }
                String title = cleanAttribute(element.attr("title"));
                String titlePart = "";
                if (!title.isEmpty()) {
                    titlePart = " \"" + title + "\"";
                }
                return "![" + alt + "]" + "(" + src + titlePart + ")";
            }));
            addRule("default", new Rule((element -> true),
                    (content, element) -> CopyNode.isBlock(element) ? "\n\n" + content + "\n\n" : content));
        }

        public Rule findRule(Node node) {
            for (Rule rule : rules) {
                if (rule.getFilter()
                        .test(node)) {
                    return rule;
                }
            }
            return null;
        }

        private void addRule(String name, Rule rule) {
            rule.setName(name);
            rules.add(rule);
        }

        private String cleanAttribute(String attribute) {
            return attribute.replaceAll("(\n+\\s*)+", "\n");
        }

        private String convertTable(Element tableElement) {
            StringBuilder markdownBuilder = new StringBuilder();

            // Process the caption
            Element caption = tableElement.selectFirst("caption");
            if (caption != null) {
                markdownBuilder.append(caption.text())
                        .append("\n\n");
            }

            // Determine the header row
            Element headerRowElement = tableElement.selectFirst("thead tr");
            List<Element> bodyRowElements;
            if (headerRowElement == null) {
                // Fallback: use the first <tr> as header if no thead exists
                List<Element> allRows = tableElement.select("tr");
                if (allRows.isEmpty()) {
                    return ""; // Empty table
                }
                headerRowElement = allRows.getFirst();
                bodyRowElements = allRows.size() > 1 ? allRows.subList(1, allRows.size()) : new ArrayList<>();
            } else {
                // If <thead> exists, use <tbody> for the body
                bodyRowElements = tableElement.select("tbody tr");
            }

            // Process header row into a list of cell strings.
            List<String> headerRow = processRow(headerRowElement);

            // Process body rows into a nested list of strings
            List<List<String>> bodyRows = new ArrayList<>();
            for (Element rowElement : bodyRowElements) {
                bodyRows.add(processRow(rowElement));
            }

            // Determine the maximum number of columns across header and body
            int columnCount = headerRow.size();
            for (List<String> row : bodyRows) {
                columnCount = Math.max(columnCount, row.size());
            }

            // Combine header and body rows for a single pass computation of max lengths
            List<List<String>> allRows = new ArrayList<>();
            allRows.add(headerRow);
            allRows.addAll(bodyRows);

            int[] maxLengths = computeMaxLengths(allRows, columnCount);

            // Build header row
            markdownBuilder.append("|");
            for (int i = 0; i < columnCount; i++) {
                String cell = i < headerRow.size() ? headerRow.get(i) : "";
                int pad = maxLengths[i] - cell.length();
                markdownBuilder.append(" ")
                        .append(cell)
                        .append(" ".repeat(pad + 1))
                        .append("|");
            }
            markdownBuilder.append("\n|");
            // Build separator row
            for (int i = 0; i < columnCount; i++) {
                markdownBuilder.append(" ")
                        .append("-".repeat(maxLengths[i]))
                        .append(" |");
            }
            markdownBuilder.append("\n");

            // Build body rows
            for (List<String> row : bodyRows) {
                markdownBuilder.append("|");
                for (int i = 0; i < columnCount; i++) {
                    String cell = i < row.size() ? row.get(i) : "";
                    int pad = maxLengths[i] - cell.length();
                    markdownBuilder.append(" ")
                            .append(cell)
                            .append(" ".repeat(pad))
                            .append(" |");
                }
                markdownBuilder.append("\n");
            }

            return markdownBuilder.toString();
        }

        /**
         * Helper method that processes a row (either header or body) and returns a list
         * of Markdown-converted cell contents.
         */
        private List<String> processRow(Element row) {
            List<String> rowContent = new ArrayList<>();
            Elements cells = row.select("td, th");
            for (Element cell : cells) {
                // Process each cell with inline conversion.
                String cellContent = process(new CopyNode(cell.outerHtml())).trim();
                rowContent.add(cellContent);
            }
            return rowContent;
        }

        /**
         * Helper method that computes the maximum length for each column across all rows.
         * A minimum width of 3 is enforced for each column.
         */
        private int[] computeMaxLengths(List<List<String>> rows, int columnCount) {
            int[] maxLengths = new int[columnCount];
            // Initialize all columns to a minimum width of 3.
            Arrays.fill(maxLengths, 3);
            for (List<String> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    maxLengths[i] = Math.max(maxLengths[i], row.get(i)
                            .length());
                }
            }
            return maxLengths;
        }

    }
}
