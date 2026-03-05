package com.vajrapulse.agents.codeanalyzer.ingest;

import com.vajrapulse.agents.codeanalyzer.model.Span;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Semantic parser for Java: produces symbols (class, method, field), spans, references, containment.
 */
public class JavaSemanticParser implements SemanticParser {

    private final JavaParser javaParser;

    public JavaSemanticParser() {
        this(new JavaParser(new ParserConfiguration()));
    }

    public JavaSemanticParser(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    @Override
    public ParseResult parse(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return new ParseResult(List.of(), List.of(), List.of(), List.of());
        }
        Optional<CompilationUnit> cuOpt = javaParser.parse(content).getResult();
        if (cuOpt.isEmpty()) {
            return new ParseResult(List.of(), List.of(), List.of(), List.of());
        }
        CompilationUnit cu = cuOpt.get();
        List<SymbolInfo> symbols = new ArrayList<>();
        List<Span> spans = new ArrayList<>();
        List<ReferenceByIndex> references = new ArrayList<>();
        List<ContainmentByIndex> containments = new ArrayList<>();

        List<TypeDeclaration<?>> types = cu.getTypes();
        for (TypeDeclaration<?> type : types) {
            int typeIndex = symbols.size();
            String kind = type instanceof ClassOrInterfaceDeclaration co
                    && co.isInterface() ? "INTERFACE" : "CLASS";
            String visibility = visibilityFromModifiers(type.getAccessSpecifier().asString());
            symbols.add(new SymbolInfo(type.getNameAsString(), kind, visibility));
            spans.add(spanFrom(filePath, type));

            if (type instanceof ClassOrInterfaceDeclaration classDecl) {
                for (MethodDeclaration method : classDecl.getMethods()) {
                    int methodIndex = symbols.size();
                    symbols.add(new SymbolInfo(method.getNameAsString(), "METHOD",
                            visibilityFromModifiers(method.getAccessSpecifier().asString())));
                    spans.add(spanFrom(filePath, method));
                    containments.add(new ContainmentByIndex(typeIndex, methodIndex));
                }
                for (FieldDeclaration field : classDecl.getFields()) {
                    for (var var : field.getVariables()) {
                        int fieldIndex = symbols.size();
                        symbols.add(new SymbolInfo(var.getNameAsString(), "FIELD",
                                visibilityFromModifiers(field.getAccessSpecifier().asString())));
                        spans.add(spanFrom(filePath, var));
                        containments.add(new ContainmentByIndex(typeIndex, fieldIndex));
                    }
                }
            }
        }

        return new ParseResult(symbols, spans, references, containments);
    }

    private static String visibilityFromModifiers(String specifier) {
        if (specifier == null || specifier.isBlank()) {
            return "package";
        }
        return specifier.toLowerCase();
    }

    private static Span spanFrom(String filePath, Node node) {
        Optional<com.github.javaparser.Range> range = node.getRange();
        if (range.isEmpty()) {
            return new Span(filePath, 0, 0, 0, 0);
        }
        var r = range.get();
        return new Span(
                filePath,
                r.begin.line,
                r.begin.column,
                r.end.line,
                r.end.column
        );
    }
}
