package io.github.furstenheim;

public class Options {
    public final String br;
    public final String hr;
    public final String emDelimiter;
    public final String strongDelimiter;
    public final HeadingStyle headingStyle;
    public final String bulletListMaker;
    public final CodeBlockStyle codeBlockStyle;
    public final LinkStyle linkStyle;

    public Options(String br, String hr, String emDelimiter, String strongDelimiter,
            HeadingStyle headingStyle, String bulletListMaker, CodeBlockStyle codeBlockStyle,
            LinkStyle linkStyle) {
        this.br = br;
        this.hr = hr;
        this.emDelimiter = emDelimiter;
        this.strongDelimiter = strongDelimiter;
        this.headingStyle = headingStyle;
        this.bulletListMaker = bulletListMaker;
        this.codeBlockStyle = codeBlockStyle;
        this.linkStyle = linkStyle;
    }
}
