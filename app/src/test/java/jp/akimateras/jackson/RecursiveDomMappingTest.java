package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

class RecursiveDomMappingTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testHtmlLikeDomTree() throws Exception {
        String json = """
                {
                    "nodeType": "document",
                    "children": [
                        {
                            "nodeType": "element",
                            "tag": "html",
                            "attributes": {
                                "lang": "en"
                            },
                            "children": [
                                {
                                    "nodeType": "element",
                                    "tag": "head",
                                    "attributes": {},
                                    "children": [
                                        {
                                            "nodeType": "element",
                                            "tag": "title",
                                            "attributes": {},
                                            "children": [
                                                {
                                                    "nodeType": "text",
                                                    "text": "Catalog"
                                                }
                                            ]
                                        }
                                    ]
                                },
                                {
                                    "nodeType": "element",
                                    "tag": "body",
                                    "attributes": {
                                        "class": "main"
                                    },
                                    "children": [
                                        {
                                            "nodeType": "element",
                                            "tag": "div",
                                            "attributes": {
                                                "id": "content"
                                            },
                                            "children": [
                                                {
                                                    "nodeType": "text",
                                                    "text": "Hello "
                                                },
                                                {
                                                    "nodeType": "element",
                                                    "tag": "span",
                                                    "attributes": {
                                                        "class": "highlight"
                                                    },
                                                    "children": [
                                                        {
                                                            "nodeType": "text",
                                                            "text": "world"
                                                        }
                                                    ]
                                                },
                                                {
                                                    "nodeType": "comment",
                                                    "text": "end marker"
                                                }
                                            ]
                                        },
                                        {
                                            "nodeType": "element",
                                            "tag": "ul",
                                            "attributes": {},
                                            "children": [
                                                {
                                                    "nodeType": "element",
                                                    "tag": "li",
                                                    "attributes": {
                                                        "data-index": "1"
                                                    },
                                                    "children": [
                                                        {
                                                            "nodeType": "text",
                                                            "text": "First"
                                                        }
                                                    ]
                                                },
                                                {
                                                    "nodeType": "element",
                                                    "tag": "li",
                                                    "attributes": {
                                                        "data-index": "2"
                                                    },
                                                    "children": [
                                                        {
                                                            "nodeType": "text",
                                                            "text": "Second"
                                                        }
                                                    ]
                                                }
                                            ]
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
                """;

        DomNode actual = MAPPER.readValue(json, DomNode.class);
        DomNode expected = new Document(
                List.of(
                        new Element(
                                "html",
                                Map.of("lang", "en"),
                                List.of(
                                        new Element(
                                                "head",
                                                Map.of(),
                                                List.of(
                                                        new Element(
                                                                "title",
                                                                Map.of(),
                                                                List.of(new Text("Catalog"))))),
                                        new Element(
                                                "body",
                                                Map.of("class", "main"),
                                                List.of(
                                                        new Element(
                                                                "div",
                                                                Map.of("id", "content"),
                                                                List.of(
                                                                        new Text("Hello "),
                                                                        new Element(
                                                                                "span",
                                                                                Map.of("class", "highlight"),
                                                                                List.of(new Text("world"))),
                                                                        new Comment("end marker"))),
                                                        new Element(
                                                                "ul",
                                                                Map.of(),
                                                                List.of(
                                                                        new Element(
                                                                                "li",
                                                                                Map.of("data-index", "1"),
                                                                                List.of(new Text("First"))),
                                                                        new Element(
                                                                                "li",
                                                                                Map.of("data-index", "2"),
                                                                                List.of(new Text("Second")))))))))));
        assertEquals(expected, actual);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "nodeType")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Document.class, name = "document"),
            @JsonSubTypes.Type(value = Element.class, name = "element"),
            @JsonSubTypes.Type(value = Text.class, name = "text"),
            @JsonSubTypes.Type(value = Comment.class, name = "comment")
    })
    sealed interface DomNode permits Document, Element, Text, Comment {
    }

    record Document(List<DomNode> children) implements DomNode {
    }

    record Element(String tag, Map<String, String> attributes, List<DomNode> children) implements DomNode {
    }

    record Text(String text) implements DomNode {
    }

    record Comment(String text) implements DomNode {
    }
}
