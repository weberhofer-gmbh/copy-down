## Copy Down

Convert HTML into Markdown with Java.

### Installation

Gradle:

```gradle
dependencies {
    compile 'io.github.furstenheim:copy_down:1.2-SNAPSHOT'
}
```

Maven:

```xml

<dependencies>
    <dependency>
        <groupId>io.github.furstenheim</groupId>
        <artifactId>copy_down</artifactId>
        <version>1.2-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### JSoup Compatibility

This library has a strong reliance on JSoup. Using a different version of it will lead to unexpected behaviours. Sadly,
Java does not allow several versions of a library (unlike Node.js) so if your project is already using JSoup that
version will have priority.

Supported versions are:

| This Library | Jsoup  |
|--------------|--------| 
| 1.0          | 1.13   |
| 1.1          | 1.15   |
| 1.2          | 1.19.1 |

### Usage

```java

import io.github.furstenheim.CopyDown;

public class Main {
    public static void main(String[] args) {
        CopyDown converter = new CopyDown();
        String myHtml = "<h1>Some title</h1><div>Some html<p>Another paragraph</p></div>";
        String markdown = converter.convert(myHtml);
        System.out.println(markdown);
        // Some title\n==========\n\nSome html\n\nAnother paragraph\n
    }
}
```

### Options

It is possible to use options for converting markdown:

```java
import io.github.furstenheim.CopyDown;
import io.github.furstenheim.Options;
import io.github.furstenheim.OptionsBuilder;

public class Main {
    public static void main(String[] args) {
        OptionsBuilder optionsBuilder = OptionsBuilder.anOptions();
        Options options = optionsBuilder.withBr("-")
                // more options
                .build();
        CopyDown converter = new CopyDown(options);
        String myHtml = "<h1>Some title</h1><div>Some html<p>Another paragraph</p></div>";
        String markdown = converter.convert(myHtml);
        System.out.println(markdown);
    }
}
```

| Option               | Valid values                                                           | Default    |
|:---------------------|:-----------------------------------------------------------------------|:-----------|
| `headingStyle`       | `SETEXT` or `ATX`                                                      | `SETEXT`   |
| `hr`                 | Any [Thematic break](http://spec.commonmark.org/0.27/#thematic-breaks) | `* * *`    |
| `bulletListMarker`   | `-`, `+`, or `*`                                                       | `*`        |
| `codeBlockStyle`     | `INDENTED` or `FENCED`                                                 | `INDENTED` |
| `fence`              | ` ``` ` or `~~~`                                                       | ` ``` `    |
| `emDelimiter`        | `_` or `*`                                                             | `_`        |
| `strongDelimiter`    | `**` or `__`                                                           | `**`       |
| `linkStyle`          | `INLINED` or `REFERENCED`                                              | `INLINED`  |
| `linkReferenceStyle` | `FULL`, `COLLAPSED`, or `SHORTCUT`                                     | `FULL`     |

### Acknowledgment

This library is a port to Java of the wonderful library [Turndown.js](https://github.com/domchristie/turndown). This
library passes the same test suite as the original library to ensure same behavior.

