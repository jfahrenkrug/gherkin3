package gherkin;

import gherkin.ast.Location;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static gherkin.Parser.ITokenMatcher;
import static gherkin.Parser.TokenType;

public class TokenMatcher implements ITokenMatcher {
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^\\s*#\\s*language\\s*:\\s*([a-zA-Z\\-_]+)\\s*$");
    private final IGherkinDialectProvider dialectProvider;
    private GherkinDialect currentDialect;
    private String activeDocStringSeparator = null;
    private int indentToRemove = 0;

    public TokenMatcher(IGherkinDialectProvider dialectProvider) {
        this.dialectProvider = dialectProvider;
        currentDialect = dialectProvider.getDefaultDialect();
    }

    public TokenMatcher() {
        this(new GherkinDialectProvider());
    }

    public GherkinDialect getCurrentDialect() {
        return currentDialect;
    }

    protected void SetTokenMatched(Token token, TokenType matchedType, String text, String keyword, Integer indent, List<GherkinLineSpan> items) {
        token.matchedType = matchedType;
        token.matchedKeyword = keyword;
        token.matchedText = text;
        token.mathcedItems = items;
        token.matchedGherkinDialect = getCurrentDialect();
        token.matchedIndent = indent != null ? indent : (token.line == null ? 0 : token.line.Indent());
        token.location = new Location(token.location.line, token.matchedIndent + 1);
    }

    private void SetTokenMatched(Token token, TokenType tokenType) {
        SetTokenMatched(token, tokenType, null, null, null, null);
    }

    @Override
    public boolean match_EOF(Token token) {
        if (token.IsEOF()) {
            SetTokenMatched(token, TokenType.EOF);
            return true;
        }
        return false;
    }

    @Override
    public boolean match_Other(Token token) {
        String text = token.line.GetLineText(indentToRemove); //take the entire line, except removing DocString indents
        SetTokenMatched(token, TokenType.Other, text, null, 0, null);
        return true;
    }

    @Override
    public boolean match_Empty(Token token) {
        if (token.line.IsEmpty()) {
            SetTokenMatched(token, TokenType.Empty);
            return true;
        }
        return false;
    }

    @Override
    public boolean match_Comment(Token token) {
        if (token.line.StartsWith(GherkinLanguageConstants.COMMENT_PREFIX)) {
            String text = token.line.GetLineText(0); //take the entire line
            SetTokenMatched(token, TokenType.Comment, text, null, 0, null);
            return true;
        }
        return false;
    }

    private ParserException CreateTokenMatcherException(Token token, String message) {
        return new ParserException.AstBuilderException(message, new Location(token.location.line, token.line.Indent() + 1));
    }

    @Override
    public boolean match_Language(Token token) {
        Matcher matcher = LANGUAGE_PATTERN.matcher(token.line.GetLineText(0));
        if (matcher.matches()) {
            String language = matcher.group(1);

            currentDialect = dialectProvider.getDialect(language);

            SetTokenMatched(token, TokenType.Language, language, null, null, null);
            return true;
        }
        return false;
    }

    @Override
    public boolean match_TagLine(Token token) {
        if (token.line.StartsWith(GherkinLanguageConstants.TAG_PREFIX)) {
            SetTokenMatched(token, TokenType.TagLine, null, null, null, token.line.GetTags());
            return true;
        }
        return false;
    }

//    public boolean matchTagLine(Token token) {
//        if (unindentedLine.charAt(0) == '@') {
//            lineSpans = new ArrayList<LineSpan>();
//
//            location.setColumn(indent + 1);
//
//            // TODO: Consider simpler Scanner based implementation like in matchTableRow()
//            int col = 0;
//            int tagStart = -1;
//            while (col < unindentedLine.length()) {
//                if (Character.isWhitespace(unindentedLine.charAt(col))) {
//                    if (tagStart > -1) {
//                        String tag = unindentedLine.substring(tagStart, col);
//                        lineSpans.add(new LineSpan(tagStart + indent + 1, tag));
//                        tagStart = -1;
//                    }
//                } else {
//                    if (tagStart == -1) {
//                        tagStart = col;
//                    }
//                }
//                col++;
//            }
//            if (tagStart > -1) {
//                String tag = unindentedLine.substring(tagStart, col);
//                lineSpans.add(new LineSpan(tagStart + indent + 1, tag));
//            }
//            return true;
//        }
//        return false;
//    }

    @Override
    public boolean match_FeatureLine(Token token) {
        return matchTitleLine(token, TokenType.FeatureLine, currentDialect.getFeatureKeywords());
    }

    @Override
    public boolean match_BackgroundLine(Token token) {
        return matchTitleLine(token, TokenType.BackgroundLine, currentDialect.getBackgroundKeywords());
    }

    @Override
    public boolean match_ScenarioLine(Token token) {
        return matchTitleLine(token, TokenType.ScenarioLine, currentDialect.getScenarioKeywords());
    }

    @Override
    public boolean match_ScenarioOutlineLine(Token token) {
        return matchTitleLine(token, TokenType.ScenarioOutlineLine, currentDialect.getScenarioOutlineKeywords());
    }

    @Override
    public boolean match_ExamplesLine(Token token) {
        return matchTitleLine(token, TokenType.ExamplesLine, currentDialect.getExamplesKeywords());
    }

    private boolean matchTitleLine(Token token, TokenType tokenType, List<String> keywords) {
        for (String keyword : keywords) {
            if (token.line.StartsWithTitleKeyword(keyword)) {
                String title = token.line.GetRestTrimmed(keyword.length() + GherkinLanguageConstants.TITLE_KEYWORD_SEPARATOR.length());
                SetTokenMatched(token, tokenType, title, keyword, null, null);
                return true;
            }
        }
        return false;
    }

    public boolean match_DocStringSeparator(Token token) {
        return activeDocStringSeparator == null
                // open
                ? match_DocStringSeparator(token, GherkinLanguageConstants.DOCSTRING_SEPARATOR, true) ||
                match_DocStringSeparator(token, GherkinLanguageConstants.DOCSTRING_ALTERNATIVE_SEPARATOR, true)
                // close
                : match_DocStringSeparator(token, activeDocStringSeparator, false);
    }

    private boolean match_DocStringSeparator(Token token, String separator, boolean isOpen) {
        if (token.line.StartsWith(separator)) {
            String contentType = null;
            if (isOpen) {
                contentType = token.line.GetRestTrimmed(separator.length());
                activeDocStringSeparator = separator;
                indentToRemove = token.line.Indent();
            } else {
                activeDocStringSeparator = null;
                indentToRemove = 0;
            }

            SetTokenMatched(token, TokenType.DocStringSeparator, contentType, null, null, null);
            return true;
        }
        return false;
    }

    @Override
    public boolean match_StepLine(Token token) {
        List<String> keywords = currentDialect.getStepKeywords();
        for (String keyword : keywords) {
            if (token.line.StartsWith(keyword)) {
                String stepText = token.line.GetRestTrimmed(keyword.length());
                SetTokenMatched(token, TokenType.StepLine, stepText, keyword, null, null);
                return true;
            }
        }
        return false;
    }

    public boolean match_TableRow(Token token) {
        if (token.line.StartsWith(GherkinLanguageConstants.TABLE_CELL_SEPARATOR)) {
            SetTokenMatched(token, TokenType.TableRow, null, null, null, token.line.GetTableCells());
            return true;
        }
        return false;
    }

//    public boolean matchTableRow(Token token) {
//        if (unindentedLine.charAt(0) == '|') {
//            lineSpans = new ArrayList<LineSpan>();
//            location.setColumn(indent + 1);
//            Scanner scanner = new Scanner(unindentedLine).useDelimiter("\\s*\\|\\s*");
//            while (scanner.hasNext()) {
//                String cell = scanner.next();
//                int column = scanner.match().start() + indent + 1;
//                lineSpans.add(new LineSpan(column, cell));
//            }
//            return true;
//        }
//        return false;
//    }

//    public boolean matchLanguage(Token token) {
//        if (unindentedLine.charAt(0) == '#') {
//            // eat space
//            int i = 1;
//            while (i < unindentedLine.length() && Character.isWhitespace(unindentedLine.charAt(i))) {
//                i++;
//            }
//            if (unindentedLine.substring(i).startsWith("language")) {
//                // eat more space
//                i += 8; // length of "language"
//                while (i < unindentedLine.length() && Character.isWhitespace(unindentedLine.charAt(i))) {
//                    i++;
//                }
//                if (unindentedLine.substring(i).startsWith(":")) {
//                    i += 1; // length of ":"
//                    while (i < unindentedLine.length() && Character.isWhitespace(unindentedLine.charAt(i))) {
//                        i++;
//                    }
//                    location.setColumn(indent + 1);
//                    this.text = unindentedLine.substring(i).trim();
//                    return true;
//                }
//            }
//        }
//        return false;
//    }

//    public boolean matchesStepLine(Token token) {
//        for (String keyword : dialect.getStepKeywords()) {
//            int stepIndex = unindentedLine.indexOf(keyword);
//            if (unindentedLine.startsWith(keyword)) {
//                this.location.setColumn(indent + stepIndex + 1);
//                this.keyword = keyword;
//                this.text = unindentedLine.substring(keyword.length()).trim();
//                return true;
//            }
//        }
//        return false;
//    }
//
//    private boolean matchesTitleLine(List<String> keywords) {
//        for (String keyword : keywords) {
//            int stepIndex = unindentedLine.indexOf(keyword + ":"); // OPTIMIZE: don't create new string
//            if (stepIndex != -1) {
//                this.location.setColumn(indent + stepIndex + 1);
//                this.keyword = keyword;
//                this.text = unindentedLine.substring(keyword.length() + 1).trim();
//                return true;
//            }
//        }
//        return false;
//    }
}
