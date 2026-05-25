package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.logError
import kotlin.math.*

/**
 * Lightweight pure-Kotlin JavaScript interpreter designed to replace Rhino for
 * our own deobfuscation use-cases.
 *
 * Supports the subset of JS that appears in obfuscated video-hosting scripts:
 *  - Variable declarations (var / let / const)
 *  - String, number and boolean literals
 *  - Arithmetic / bitwise / comparison / logical operators
 *  - String concatenation with +
 *  - String methods: split, join, reverse, replace, charAt, charCodeAt,
 *                    fromCharCode, substr, substring, slice, indexOf,
 *                    trim, toLowerCase, toUpperCase, toString, length
 *  - Array literals and methods: join, reverse, split, push, pop,
 *                                 map, filter, forEach, length
 *  - parseInt / parseFloat / isNaN / isFinite
 *  - Math.*  (sin, cos, floor, ceil, round, abs, pow, sqrt, log, max, min, random)
 *  - String.fromCharCode
 *  - Ternary operator (a ? b : c)
 *  - if / else / while / for statements
 *  - Function declarations and calls (named and anonymous)
 *  - return / break / continue
 *  - Template literals (back-tick strings)
 *  - typeof
 *
 * Usage:
 *   val result = JsInterpreter().eval("var x = 1+2; x")
 *   // result == 3.0 (numbers are always Double internally)
 *
 *   // Drop-in replacement for the old Rhino-based evaluateString pattern:
 *   val ctx = JsContext()
 *   ctx.eval("var url = 'https:' + computeSuffix()")
 *   val url = ctx["url"]   // retrieves the variable as a String
 */

/**
 * Convert any JS runtime value to its JavaScript string representation.
 * Mirrors what JS `String(value)` would produce.
 */
fun jsValueToString(v: Any?): String = toJsString(v)

/**
 * Stateful JS execution context.  Keeps variables alive between [eval] calls,
 * mimicking the Rhino "scope" object that extensions used to hold on to.
 */
class JsContext {
    private val interpreter = JsInterpreter()

    /** Evaluate [code] in this context.  Returns the last expression value. */
    fun eval(code: String): Any? = interpreter.eval(code)

    /** Retrieve a variable set by previously evaluated code. */
    operator fun get(name: String): Any? = interpreter.getVar(name)

    /** Expose a Kotlin value to subsequently evaluated JS code. */
    operator fun set(name: String, value: Any?) = interpreter.setVar(name, value)
}

/**
 * Evaluate [js] and return its last value.  Convenience wrapper for one-shot
 * evaluations, equivalent to the old `rhino.evaluateString(scope, js, ...)`.
 */
fun evalJs(js: String): Any? = JsInterpreter().eval(js)

private enum class TT {
    NUMBER, STRING, IDENT, PLUS, MINUS, STAR, SLASH, PERCENT, EQ, EQEQ, EQEQEQ,
    NEQ, NEQEQ, LT, LTEQ, GT, GTEQ, AND, OR, NOT, AMP, PIPE, CARET, TILDE,
    LSHIFT, RSHIFT, URSHIFT, PLUSEQ, MINUSEQ, STAREQ, SLASHEQ, PERCENTEQ,
    PLUSPLUS, MINUSMINUS, DOT, COMMA, SEMI, COLON, QUESTION, LPAREN, RPAREN,
    LBRACE, RBRACE, LBRACKET, RBRACKET, EOF
}

private data class Token(val type: TT, val raw: String, val pos: Int)

private class Lexer(private val src: String) {
    var pos = 0
    private val tokens = mutableListOf<Token>()
    private var idx = 0

    init { tokenize() }

    private fun tokenize() {
        while (pos < src.length) {
            skipWhitespaceAndComments()
            if (pos >= src.length) break
            val c = src[pos]
            when {
                c.isDigit() || (c == '.' && pos + 1 < src.length && src[pos + 1].isDigit()) -> readNumber()
                c == '"' || c == '\'' || c == '`' -> readString(c)
                c.isLetter() || c == '_' || c == '$' -> readIdent()
                else -> readOp()
            }
        }
        tokens.add(Token(TT.EOF, "", pos))
    }

    private fun skipWhitespaceAndComments() {
        while (pos < src.length) {
            val c = src[pos]
            if (c.isWhitespace()) { pos++; continue }
            if (c == '/' && pos + 1 < src.length) {
                when (src[pos + 1]) {
                    '/' -> { pos = src.indexOf('\n', pos).let { if (it < 0) src.length else it + 1 }; continue }
                    '*' -> {
                        val end = src.indexOf("*/", pos + 2)
                        pos = if (end < 0) src.length else end + 2; continue
                    }
                    else -> {}
                }
            }
            break
        }
    }

    private fun readNumber() {
        val start = pos
        if (pos + 1 < src.length && src[pos] == '0' &&
            (src[pos + 1] == 'x' || src[pos + 1] == 'X')
        ) {
            pos += 2
            while (pos < src.length && src[pos].isLetterOrDigit()) pos++
        } else {
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
        }
        tokens.add(Token(TT.NUMBER, src.substring(start, pos), start))
    }

    private fun readString(quote: Char) {
        val start = pos++
        val sb = StringBuilder()
        if (quote == '`') {
            // template literal. We flatten it to a plain string (no interpolation yet)
            while (pos < src.length && src[pos] != '`') {
                if (src[pos] == '\\' && pos + 1 < src.length) {
                    sb.append(unescape(src[++pos])); pos++
                } else sb.append(src[pos++])
            }
            if (pos < src.length) pos++ // consume closing `
        } else {
            while (pos < src.length && src[pos] != quote) {
                if (src[pos] == '\\' && pos + 1 < src.length) {
                    sb.append(unescape(src[++pos])); pos++
                } else sb.append(src[pos++])
            }
            if (pos < src.length) pos++
        }
        tokens.add(Token(TT.STRING, sb.toString(), start))
    }

    private fun unescape(c: Char) = when (c) {
        'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'
        '\'' -> '\''; '"' -> '"'; '\\' -> '\\'; '`' -> '`'
        else -> c
    }

    private fun readIdent() {
        val start = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_' || src[pos] == '$')) pos++
        tokens.add(Token(TT.IDENT, src.substring(start, pos), start))
    }

    private fun readOp() {
        val start = pos
        val c = src[pos++]
        val next = if (pos < src.length) src[pos] else '\u0000'
        val nn = if (pos + 1 < src.length) src[pos + 1] else '\u0000'
        fun adv(t: TT, n: Int = 0): Token { pos += n; return Token(t, "", start) }
        val tok = when (c) {
            '+' -> when (next) { '+' -> adv(TT.PLUSPLUS, 1); '=' -> adv(TT.PLUSEQ, 1); else -> Token(TT.PLUS, "+", start) }
            '-' -> when (next) { '-' -> adv(TT.MINUSMINUS, 1); '=' -> adv(TT.MINUSEQ, 1); else -> Token(TT.MINUS, "-", start) }
            '*' -> if (next == '=') adv(TT.STAREQ, 1) else Token(TT.STAR, "*", start)
            '/' -> if (next == '=') adv(TT.SLASHEQ, 1) else Token(TT.SLASH, "/", start)
            '%' -> if (next == '=') adv(TT.PERCENTEQ, 1) else Token(TT.PERCENT, "%", start)
            '=' -> when { next == '=' && nn == '=' -> adv(TT.EQEQEQ, 2); next == '=' -> adv(TT.EQEQ, 1); else -> Token(TT.EQ, "=", start) }
            '!' -> when { next == '=' && nn == '=' -> adv(TT.NEQEQ, 2); next == '=' -> adv(TT.NEQ, 1); else -> Token(TT.NOT, "!", start) }
            '<' -> when { next == '<' -> adv(TT.LSHIFT, 1); next == '=' -> adv(TT.LTEQ, 1); else -> Token(TT.LT, "<", start) }
            '>' -> when { next == '>' && nn == '>' -> adv(TT.URSHIFT, 2); next == '>' -> adv(TT.RSHIFT, 1); next == '=' -> adv(TT.GTEQ, 1); else -> Token(TT.GT, ">", start) }
            '&' -> if (next == '&') adv(TT.AND, 1) else Token(TT.AMP, "&", start)
            '|' -> if (next == '|') adv(TT.OR, 1) else Token(TT.PIPE, "|", start)
            '^' -> Token(TT.CARET, "^", start)
            '~' -> Token(TT.TILDE, "~", start)
            '.' -> Token(TT.DOT, ".", start)
            ',' -> Token(TT.COMMA, ",", start)
            ';' -> Token(TT.SEMI, ";", start)
            ':' -> Token(TT.COLON, ":", start)
            '?' -> Token(TT.QUESTION, "?", start)
            '(' -> Token(TT.LPAREN, "(", start)
            ')' -> Token(TT.RPAREN, ")", start)
            '{' -> Token(TT.LBRACE, "{", start)
            '}' -> Token(TT.RBRACE, "}", start)
            '[' -> Token(TT.LBRACKET, "[", start)
            ']' -> Token(TT.RBRACKET, "]", start)
            else -> Token(TT.SEMI, "", start) // swallow unknown
        }
        tokens.add(tok)
    }

    fun peek(offset: Int = 0): Token = tokens.getOrElse(idx + offset) { tokens.last() }
    fun consume(): Token = tokens[idx++]
    fun expect(t: TT): Token {
        val tok = consume()
        if (tok.type != t) throw RuntimeException("Expected $t got ${tok.type} ('${tok.raw}') at ${tok.pos}")
        return tok
    }
    fun matchIf(t: TT): Boolean = if (peek().type == t) { consume(); true } else false
}

private sealed class Node
private data class NumLit(val v: Double) : Node()
private data class StrLit(val v: String) : Node()
private data class BoolLit(val v: Boolean) : Node()
private object NullLit : Node()
private object UndefinedLit : Node()
private data class Ident(val name: String) : Node()
private data class ArrayLit(val elems: List<Node>) : Node()
private data class ObjLit(val pairs: List<Pair<String, Node>>) : Node()
private data class MemberExpr(val obj: Node, val prop: Node, val computed: Boolean) : Node()
private data class CallExpr(val callee: Node, val args: List<Node>) : Node()
private data class NewExpr(val callee: Node, val args: List<Node>) : Node()
private data class UnaryExpr(val op: String, val expr: Node, val prefix: Boolean) : Node()
private data class BinExpr(val op: String, val left: Node, val right: Node) : Node()
private data class AssignExpr(val op: String, val left: Node, val right: Node) : Node()
private data class CondExpr(val test: Node, val cons: Node, val alt: Node) : Node()
private data class TypeofExpr(val expr: Node) : Node()
private data class SeqExpr(val exprs: List<Node>) : Node()
private data class FuncExpr(val name: String?, val params: List<String>, val body: List<Node>) : Node()
private data class VarDecl(val decls: List<Pair<String, Node?>>) : Node()
private data class ExprStmt(val expr: Node) : Node()
private data class BlockStmt(val stmts: List<Node>) : Node()
private data class ReturnStmt(val expr: Node?) : Node()
private data class IfStmt(val test: Node, val cons: Node, val alt: Node?) : Node()
private data class WhileStmt(val test: Node, val body: Node) : Node()
private data class ForStmt(val init: Node?, val test: Node?, val update: Node?, val body: Node) : Node()
private data class ForInStmt(val decl: String, val obj: Node, val body: Node) : Node()
private object BreakStmt : Node()
private object ContinueStmt : Node()
private data class TryCatch(val body: List<Node>, val catchParam: String?, val catchBody: List<Node>?, val finallyBody: List<Node>?) : Node()
private data class ThrowStmt(val expr: Node) : Node()

private class Parser(private val lex: Lexer) {

    fun parseProgram(): List<Node> {
        val stmts = mutableListOf<Node>()
        while (lex.peek().type != TT.EOF) stmts.add(parseStatement())
        return stmts
    }

    private fun parseStatement(): Node {
        return when (lex.peek().type) {
            TT.LBRACE -> parseBlock()
            TT.SEMI -> { lex.consume(); BlockStmt(emptyList()) }
            TT.IDENT -> when (lex.peek().raw) {
                "var", "let", "const" -> parseVarDecl()
                "function" -> parseFunctionDecl()
                "return" -> parseReturn()
                "if" -> parseIf()
                "while" -> parseWhile()
                "for" -> parseFor()
                "break" -> { lex.consume(); lex.matchIf(TT.SEMI); BreakStmt }
                "continue" -> { lex.consume(); lex.matchIf(TT.SEMI); ContinueStmt }
                "try" -> parseTryCatch()
                "throw" -> parseThrow()
                "typeof" -> parseExprStmt()
                else -> parseExprStmt()
            }
            else -> parseExprStmt()
        }
    }

    private fun parseBlock(): BlockStmt {
        lex.expect(TT.LBRACE)
        val stmts = mutableListOf<Node>()
        while (lex.peek().type != TT.RBRACE && lex.peek().type != TT.EOF) stmts.add(parseStatement())
        lex.expect(TT.RBRACE)
        return BlockStmt(stmts)
    }

    private fun parseVarDecl(): VarDecl {
        lex.consume() // var / let / const
        val decls = mutableListOf<Pair<String, Node?>>()
        do {
            val name = lex.expect(TT.IDENT).raw
            val init = if (lex.matchIf(TT.EQ)) parseAssign() else null
            decls.add(name to init)
        } while (lex.matchIf(TT.COMMA))
        lex.matchIf(TT.SEMI)
        return VarDecl(decls)
    }

    private fun parseFunctionDecl(): Node {
        lex.consume() // "function"
        val name = if (lex.peek().type == TT.IDENT) lex.consume().raw else null
        val params = parseParams()
        val body = parseBlock().stmts
        return if (name != null) VarDecl(listOf(name to FuncExpr(name, params, body))) else FuncExpr(name, params, body)
    }

    private fun parseParams(): List<String> {
        lex.expect(TT.LPAREN)
        val params = mutableListOf<String>()
        while (lex.peek().type != TT.RPAREN && lex.peek().type != TT.EOF) {
            params.add(lex.expect(TT.IDENT).raw)
            if (!lex.matchIf(TT.COMMA)) break
        }
        lex.expect(TT.RPAREN)
        return params
    }

    private fun parseReturn(): ReturnStmt {
        lex.consume()
        val expr = if (lex.peek().type == TT.SEMI || lex.peek().type == TT.RBRACE || lex.peek().type == TT.EOF) null else parseAssign()
        lex.matchIf(TT.SEMI)
        return ReturnStmt(expr)
    }

    private fun parseIf(): IfStmt {
        lex.consume()
        lex.expect(TT.LPAREN)
        val test = parseAssign()
        lex.expect(TT.RPAREN)
        val cons = parseStatement()
        val alt = if (lex.peek().type == TT.IDENT && lex.peek().raw == "else") { lex.consume(); parseStatement() } else null
        return IfStmt(test, cons, alt)
    }

    private fun parseWhile(): WhileStmt {
        lex.consume()
        lex.expect(TT.LPAREN)
        val test = parseAssign()
        lex.expect(TT.RPAREN)
        return WhileStmt(test, parseStatement())
    }

    private fun parseFor(): Node {
        lex.consume()
        lex.expect(TT.LPAREN)
        // for..in check
        if (lex.peek().type == TT.IDENT && (lex.peek().raw == "var" || lex.peek().raw == "let" || lex.peek().raw == "const")) {
            val savedIdx = lex.peek()
            lex.consume()
            val varName = lex.expect(TT.IDENT).raw
            if (lex.peek().type == TT.IDENT && lex.peek().raw == "in") {
                lex.consume()
                val obj = parseAssign()
                lex.expect(TT.RPAREN)
                return ForInStmt(varName, obj, parseStatement())
            }
            // backtrack is hard so we just reconstruct as normal for
            val init: Node? = if (lex.peek().type != TT.SEMI) {
                val initExpr = if (lex.matchIf(TT.EQ)) parseAssign() else null
                VarDecl(listOf(varName to initExpr))
            } else VarDecl(listOf(varName to null))
            lex.matchIf(TT.SEMI)
            val test = if (lex.peek().type != TT.SEMI) parseAssign() else null
            lex.matchIf(TT.SEMI)
            val update = if (lex.peek().type != TT.RPAREN) parseAssign() else null
            lex.expect(TT.RPAREN)
            return ForStmt(init, test, update, parseStatement())
        }
        val init = if (lex.peek().type != TT.SEMI) parseAssign() else null
        lex.matchIf(TT.SEMI)
        val test = if (lex.peek().type != TT.SEMI) parseAssign() else null
        lex.matchIf(TT.SEMI)
        val update = if (lex.peek().type != TT.RPAREN) parseAssign() else null
        lex.expect(TT.RPAREN)
        return ForStmt(init, test, update, parseStatement())
    }

    private fun parseTryCatch(): TryCatch {
        lex.consume() // "try"
        val body = parseBlock().stmts
        var catchParam: String? = null
        var catchBody: List<Node>? = null
        if (lex.peek().type == TT.IDENT && lex.peek().raw == "catch") {
            lex.consume()
            if (lex.matchIf(TT.LPAREN)) {
                catchParam = lex.expect(TT.IDENT).raw
                lex.expect(TT.RPAREN)
            }
            catchBody = parseBlock().stmts
        }
        val finallyBody = if (lex.peek().type == TT.IDENT && lex.peek().raw == "finally") {
            lex.consume(); parseBlock().stmts
        } else null
        return TryCatch(body, catchParam, catchBody, finallyBody)
    }

    private fun parseThrow(): ThrowStmt {
        lex.consume()
        val expr = parseAssign()
        lex.matchIf(TT.SEMI)
        return ThrowStmt(expr)
    }

    private fun parseExprStmt(): Node {
        val expr = parseSeq()
        lex.matchIf(TT.SEMI)
        return ExprStmt(expr)
    }

    private fun parseSeq(): Node {
        val first = parseAssign()
        if (lex.peek().type != TT.COMMA) return first
        val exprs = mutableListOf(first)
        while (lex.matchIf(TT.COMMA)) exprs.add(parseAssign())
        return SeqExpr(exprs)
    }

    private fun parseAssign(): Node {
        val left = parseTernary()
        val op = when (lex.peek().type) {
            TT.EQ -> "="; TT.PLUSEQ -> "+="; TT.MINUSEQ -> "-="; TT.STAREQ -> "*="; TT.SLASHEQ -> "/="; TT.PERCENTEQ -> "%="
            else -> return left
        }
        lex.consume()
        return AssignExpr(op, left, parseAssign())
    }

    private fun parseTernary(): Node {
        val test = parseOr()
        if (!lex.matchIf(TT.QUESTION)) return test
        val cons = parseAssign()
        lex.expect(TT.COLON)
        return CondExpr(test, cons, parseAssign())
    }

    private fun parseOr() = parseBin(::parseAnd, TT.OR to "||")
    private fun parseAnd() = parseBin(::parseBitor, TT.AND to "&&")
    private fun parseBitor() = parseBin(::parseBitxor, TT.PIPE to "|")
    private fun parseBitxor() = parseBin(::parseBitand, TT.CARET to "^")
    private fun parseBitand() = parseBin(::parseEq, TT.AMP to "&")
    private fun parseEq() = parseBin(::parseRel, TT.EQEQ to "==", TT.EQEQEQ to "===", TT.NEQ to "!=", TT.NEQEQ to "!==")
    private fun parseRel() = parseBin(::parseShift, TT.LT to "<", TT.LTEQ to "<=", TT.GT to ">", TT.GTEQ to ">=")
    private fun parseShift() = parseBin(::parseAdd, TT.LSHIFT to "<<", TT.RSHIFT to ">>", TT.URSHIFT to ">>>")
    private fun parseAdd() = parseBin(::parseMul, TT.PLUS to "+", TT.MINUS to "-")
    private fun parseMul() = parseBin(::parseUnary, TT.STAR to "*", TT.SLASH to "/", TT.PERCENT to "%")

    private fun parseBin(next: () -> Node, vararg ops: Pair<TT, String>): Node {
        var left = next()
        while (true) {
            val op = ops.firstOrNull { it.first == lex.peek().type } ?: break
            lex.consume()
            left = BinExpr(op.second, left, next())
        }
        return left
    }

    private fun parseUnary(): Node {
        return when (lex.peek().type) {
            TT.MINUS -> { lex.consume(); UnaryExpr("-", parseUnary(), true) }
            TT.PLUS -> { lex.consume(); UnaryExpr("+", parseUnary(), true) }
            TT.NOT -> { lex.consume(); UnaryExpr("!", parseUnary(), true) }
            TT.TILDE -> { lex.consume(); UnaryExpr("~", parseUnary(), true) }
            TT.PLUSPLUS -> { lex.consume(); UnaryExpr("++", parsePostfix(), true) }
            TT.MINUSMINUS -> { lex.consume(); UnaryExpr("--", parsePostfix(), true) }
            TT.IDENT -> when (lex.peek().raw) {
                "typeof" -> { lex.consume(); TypeofExpr(parseUnary()) }
                "void" -> { lex.consume(); parseUnary(); UndefinedLit }
                "new" -> parseNew()
                else -> parsePostfix()
            }
            else -> parsePostfix()
        }
    }

    private fun parseNew(): Node {
        lex.consume() // "new"
        val callee = parsePrimary()
        val args = if (lex.peek().type == TT.LPAREN) parseArgs() else emptyList()
        return NewExpr(callee, args)
    }

    private fun parsePostfix(): Node {
        var expr = parseCall()
        while (true) {
            expr = when (lex.peek().type) {
                TT.PLUSPLUS -> { lex.consume(); UnaryExpr("++", expr, false) }
                TT.MINUSMINUS -> { lex.consume(); UnaryExpr("--", expr, false) }
                else -> break
            }
        }
        return expr
    }

    private fun parseCall(): Node {
        var expr = parsePrimary()
        while (true) {
            expr = when (lex.peek().type) {
                TT.LPAREN -> CallExpr(expr, parseArgs())
                TT.DOT -> {
                    lex.consume()
                    val prop = lex.expect(TT.IDENT).raw
                    MemberExpr(expr, StrLit(prop), false)
                }
                TT.LBRACKET -> {
                    lex.consume()
                    val prop = parseAssign()
                    lex.expect(TT.RBRACKET)
                    MemberExpr(expr, prop, true)
                }
                else -> break
            }
        }
        return expr
    }

    private fun parseArgs(): List<Node> {
        lex.expect(TT.LPAREN)
        val args = mutableListOf<Node>()
        while (lex.peek().type != TT.RPAREN && lex.peek().type != TT.EOF) {
            args.add(parseAssign())
            if (!lex.matchIf(TT.COMMA)) break
        }
        lex.expect(TT.RPAREN)
        return args
    }

    private fun parsePrimary(): Node {
        val tok = lex.peek()
        return when (tok.type) {
            TT.NUMBER -> {
                lex.consume()
                NumLit(
                    tok.raw.toDoubleOrNull()
                        ?: if (tok.raw.startsWith("0x") || tok.raw.startsWith("0X"))
                            tok.raw.drop(2).toLong(16).toDouble()
                    else Double.NaN
                )
            }
            TT.STRING -> { lex.consume(); StrLit(tok.raw) }
            TT.LPAREN -> {
                lex.consume()
                val expr = parseSeq()
                lex.expect(TT.RPAREN)
                expr
            }
            TT.LBRACKET -> {
                lex.consume()
                val elems = mutableListOf<Node>()
                while (lex.peek().type != TT.RBRACKET && lex.peek().type != TT.EOF) {
                    elems.add(parseAssign()); lex.matchIf(TT.COMMA)
                }
                lex.expect(TT.RBRACKET)
                ArrayLit(elems)
            }
            TT.LBRACE -> {
                lex.consume()
                val pairs = mutableListOf<Pair<String, Node>>()
                while (lex.peek().type != TT.RBRACE && lex.peek().type != TT.EOF) {
                    val key = when (lex.peek().type) {
                        TT.IDENT, TT.STRING -> lex.consume().raw
                        TT.NUMBER -> lex.consume().raw
                        else -> lex.consume().raw
                    }
                    lex.expect(TT.COLON)
                    pairs.add(key to parseAssign())
                    lex.matchIf(TT.COMMA)
                }
                lex.expect(TT.RBRACE)
                ObjLit(pairs)
            }
            TT.IDENT -> {
                val name = tok.raw
                lex.consume()
                when (name) {
                    "true" -> BoolLit(true)
                    "false" -> BoolLit(false)
                    "null" -> NullLit
                    "undefined" -> UndefinedLit
                    "function" -> {
                        val fname = if (lex.peek().type == TT.IDENT) lex.consume().raw else null
                        val params = parseParams()
                        FuncExpr(fname, params, parseBlock().stmts)
                    }
                    else -> Ident(name)
                }
            }
            else -> { lex.consume(); UndefinedLit }
        }
    }
}

private class ReturnSignal(val value: Any?) : Throwable()
private object BreakSignal : Throwable()
private object ContinueSignal : Throwable()
private class ThrowSignal(val value: Any?) : Throwable()

private fun toNumber(v: Any?): Double = when (v) {
    null, is Unit -> 0.0
    is Double -> v
    is Boolean -> if (v) 1.0 else 0.0
    is String -> v.trim().toDoubleOrNull() ?: if (v.startsWith("0x") || v.startsWith("0X")) v.drop(2).toLongOrNull(16)?.toDouble() ?: Double.NaN else Double.NaN
    is JsList -> Double.NaN
    else -> Double.NaN
}

private fun toBoolean(v: Any?): Boolean = when (v) {
    null, is Unit -> false
    is Boolean -> v
    is Double -> v != 0.0 && !v.isNaN()
    is String -> v.isNotEmpty()
    is JsList -> true
    is JsObject -> true
    else -> true
}

private fun toJsString(v: Any?): String = when (v) {
    null -> "null"
    is Unit -> "undefined"
    is Boolean -> v.toString()
    is Double -> if (v == floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
    is String -> v
    is JsList -> v.elements.joinToString(",") { toJsString(it) }
    is JsObject -> "[object Object]"
    else -> v.toString()
}

private fun strictEq(a: Any?, b: Any?): Boolean = when {
    a is Double && b is Double -> a == b
    a is String && b is String -> a == b
    a is Boolean && b is Boolean -> a == b
    a == null && b == null -> true
    a is Unit && b is Unit -> true
    else -> a === b
}

private fun looseEq(a: Any?, b: Any?): Boolean {
    if (strictEq(a, b)) return true
    // number coercion
    val an = a is Double || a is Boolean
    val bn = b is Double || b is Boolean
    if (an || bn) return toNumber(a) == toNumber(b)
    if (a is String || b is String) return toJsString(a) == toJsString(b)
    return false
}

private class JsList(val elements: MutableList<Any?> = mutableListOf()) {
    var length: Int
        get() = elements.size
        set(v) { while (elements.size < v) elements.add(Unit); while (elements.size > v) elements.removeAt(elements.size - 1) }

    operator fun get(idx: Int): Any? = elements.getOrElse(idx) { Unit }
    operator fun set(idx: Int, v: Any?) { while (elements.size <= idx) elements.add(Unit); elements[idx] = v }
}

private class JsObject(val props: MutableMap<String, Any?> = mutableMapOf())

private class JsFunction(
    val name: String?,
    val params: List<String>,
    val body: List<Node>,
    val closure: Scope
)

private class Scope(val parent: Scope? = null) {
    val vars = mutableMapOf<String, Any?>()

    fun get(name: String): Any? {
        if (vars.containsKey(name)) return vars[name]
        return parent?.get(name) ?: Unit
    }

    fun set(name: String, value: Any?) {
        val scope = findOwner(name)
        if (scope != null) scope.vars[name] = value else vars[name] = value
    }

    fun define(name: String, value: Any?) { vars[name] = value }

    private fun findOwner(name: String): Scope? =
        if (vars.containsKey(name)) this else parent?.findOwner(name)
}

class JsInterpreter {
    private val globalScope = Scope()

    init { installGlobals() }

    private fun installGlobals() {
        val mathObj = JsObject(mutableMapOf(
            "PI" to Math.PI, "E" to Math.E,
            "floor" to nativeFn { args -> floor(toNumber(args.getOrNull(0))) },
            "ceil" to nativeFn { args -> ceil(toNumber(args.getOrNull(0))) },
            "round" to nativeFn { args -> round(toNumber(args.getOrNull(0))) },
            "abs" to nativeFn { args -> abs(toNumber(args.getOrNull(0))) },
            "sqrt" to nativeFn { args -> sqrt(toNumber(args.getOrNull(0))) },
            "pow" to nativeFn { args -> toNumber(args.getOrNull(0)).pow(toNumber(args.getOrNull(1))) },
            "log" to nativeFn { args -> ln(toNumber(args.getOrNull(0))) },
            "sin" to nativeFn { args -> sin(toNumber(args.getOrNull(0))) },
            "cos" to nativeFn { args -> cos(toNumber(args.getOrNull(0))) },
            "tan" to nativeFn { args -> tan(toNumber(args.getOrNull(0))) },
            "max" to nativeFn { args -> args.maxOfOrNull { toNumber(it) } ?: Double.NEGATIVE_INFINITY },
            "min" to nativeFn { args -> args.minOfOrNull { toNumber(it) } ?: Double.POSITIVE_INFINITY },
            "random" to nativeFn { _ -> Math.random() },
            "trunc" to nativeFn { args -> truncate(toNumber(args.getOrNull(0))) },
            "log2" to nativeFn { args -> log2(toNumber(args.getOrNull(0))) },
            "log10" to nativeFn { args -> log10(toNumber(args.getOrNull(0))) },
        ))
        globalScope.define("Math", mathObj)

        // for String.fromCharCode
        val stringObj = JsObject(mutableMapOf(
            "fromCharCode" to nativeFn { args -> args.joinToString("") { toNumber(it).toInt().toChar().toString() } }
        ))
        globalScope.define("String", stringObj)

        globalScope.define("parseInt", nativeFn { args ->
            val s = toJsString(args.getOrNull(0)).trim()
            val radix = args.getOrNull(1)?.let { toNumber(it).toInt() }?.takeIf { it in 2..36 } ?: 10
            try { s.toLong(radix).toDouble() } catch (_: Exception) { Double.NaN }
        })
        globalScope.define("parseFloat", nativeFn { args -> toNumber(args.getOrNull(0)) })
        globalScope.define("isNaN", nativeFn { args -> toNumber(args.getOrNull(0)).isNaN() })
        globalScope.define("isFinite", nativeFn { args -> toNumber(args.getOrNull(0)).isFinite() })
        globalScope.define("decodeURIComponent", nativeFn { args -> java.net.URLDecoder.decode(toJsString(args.getOrNull(0)), "UTF-8") })
        globalScope.define("encodeURIComponent", nativeFn { args -> java.net.URLEncoder.encode(toJsString(args.getOrNull(0)), "UTF-8") })
        globalScope.define("escape", nativeFn { args -> java.net.URLEncoder.encode(toJsString(args.getOrNull(0)), "UTF-8") })
        globalScope.define("unescape", nativeFn { args -> java.net.URLDecoder.decode(toJsString(args.getOrNull(0)), "UTF-8") })
        globalScope.define("eval", nativeFn { args -> eval(toJsString(args.getOrNull(0))) })
        globalScope.define("undefined", Unit)
        globalScope.define("NaN", Double.NaN)
        globalScope.define("Infinity", Double.POSITIVE_INFINITY)

        globalScope.define("Array", nativeFn { args ->
            if (args.size == 1 && args[0] is Double) JsList(MutableList((args[0] as Double).toInt()) { Unit })
            else JsList(args.toMutableList())
        })

        globalScope.define("Object", JsObject(mutableMapOf(
            "keys" to nativeFn { args ->
                when (val o = args.getOrNull(0)) {
                    is JsObject -> JsList(o.props.keys.map { it as Any? }.toMutableList())
                    else -> JsList()
                }
            },
            "values" to nativeFn { args ->
                when (val o = args.getOrNull(0)) {
                    is JsObject -> JsList(o.props.values.toMutableList())
                    else -> JsList()
                }
            }
        )))

        // console.log (no-op for silence, but avoids errors)
        val consoleObj = JsObject(mutableMapOf(
            "log" to nativeFn { _ -> Unit },
            "error" to nativeFn { _ -> Unit },
            "warn" to nativeFn { _ -> Unit },
        ))
        globalScope.define("console", consoleObj)
    }

    private fun nativeFn(fn: (List<Any?>) -> Any?): Any? = NativeFn(fn)

    fun eval(code: String): Any? {
        return try {
            val lexer = Lexer(code)
            val parser = Parser(lexer)
            val stmts = parser.parseProgram()
            var last: Any? = Unit
            for (stmt in stmts) last = execNode(stmt, globalScope)
            last
        } catch (r: ReturnSignal) {
            r.value
        } catch (e: Exception) {
            logError(e)
            Unit
        }
    }

    fun getVar(name: String): Any? = globalScope.get(name).let { if (it is Unit) null else it }
    fun setVar(name: String, value: Any?) = globalScope.define(name, value)

    private fun execNode(node: Node, scope: Scope): Any? = when (node) {
        is VarDecl -> {
            for ((name, init) in node.decls) scope.define(name, init?.let { evalExpr(it, scope) })
            Unit
        }
        is ExprStmt -> evalExpr(node.expr, scope)
        is BlockStmt -> {
            val inner = Scope(scope)
            var last: Any? = Unit
            for (s in node.stmts) last = execNode(s, inner)
            last
        }
        is ReturnStmt -> throw ReturnSignal(node.expr?.let { evalExpr(it, scope) })
        is IfStmt -> {
            if (toBoolean(evalExpr(node.test, scope))) execNode(node.cons, scope)
            else node.alt?.let { execNode(it, scope) }
        }
        is WhileStmt -> {
            try {
                while (toBoolean(evalExpr(node.test, scope))) {
                    try { execNode(node.body, scope) } catch (_: ContinueSignal) {}
                }
            } catch (_: BreakSignal) {}
            Unit
        }
        is ForStmt -> {
            val inner = Scope(scope)
            node.init?.let { execNode(it, inner) }
            try {
                while (node.test == null || toBoolean(evalExpr(node.test, inner))) {
                    try { execNode(node.body, inner) } catch (_: ContinueSignal) {}
                    node.update?.let { evalExpr(it, inner) }
                }
            } catch (_: BreakSignal) {}
            Unit
        }
        is ForInStmt -> {
            val obj = evalExpr(node.obj, scope)
            val inner = Scope(scope)
            try {
                when (obj) {
                    is JsObject -> for (key in obj.props.keys) {
                        inner.define(node.decl, key)
                        try { execNode(node.body, inner) } catch (_: ContinueSignal) {}
                    }
                    is JsList -> for (i in obj.elements.indices) {
                        inner.define(node.decl, i.toDouble())
                        try { execNode(node.body, inner) } catch (_: ContinueSignal) {}
                    }
                    else -> {}
                }
            } catch (_: BreakSignal) {}
            Unit
        }
        is TryCatch -> {
            try {
                for (s in node.body) execNode(s, scope)
            } catch (e: ThrowSignal) {
                if (node.catchBody != null) {
                    val inner = Scope(scope)
                    if (node.catchParam != null) inner.define(node.catchParam, e.value)
                    for (s in node.catchBody) execNode(s, inner)
                }
            } catch (_: Exception) {
                // swallow other exceptions inside try (e.g. runtime errors)
            } finally {
                node.finallyBody?.forEach { execNode(it, scope) }
            }
            Unit
        }
        is ThrowStmt -> throw ThrowSignal(evalExpr(node.expr, scope))
        is BreakStmt -> throw BreakSignal
        is ContinueStmt -> throw ContinueSignal
        else -> evalExpr(node, scope)
    }

    private fun evalExpr(node: Node, scope: Scope): Any? = when (node) {
        is NumLit -> node.v
        is StrLit -> node.v
        is BoolLit -> node.v
        NullLit -> null
        UndefinedLit -> Unit
        is Ident -> scope.get(node.name)
        is ArrayLit -> JsList(node.elems.map { evalExpr(it, scope) }.toMutableList())
        is ObjLit -> JsObject(node.pairs.associate { (k, v) -> k to evalExpr(v, scope) }.toMutableMap())
        is FuncExpr -> {
            val fn = JsFunction(node.name, node.params, node.body, scope)
            if (node.name != null) scope.define(node.name, fn)
            fn
        }
        is SeqExpr -> node.exprs.fold(Unit as Any?) { _, e -> evalExpr(e, scope) }
        is TypeofExpr -> {
            val v = try { evalExpr(node.expr, scope) } catch (_: Exception) { Unit }
            when (v) {
                is Unit -> "undefined"
                null -> "object"
                is Double -> "number"
                is Boolean -> "boolean"
                is String -> "string"
                is JsFunction, is NativeFn -> "function"
                else -> "object"
            }
        }
        is UnaryExpr -> evalUnary(node, scope)
        is BinExpr -> evalBinary(node, scope)
        is AssignExpr -> evalAssign(node, scope)
        is CondExpr -> if (toBoolean(evalExpr(node.test, scope))) evalExpr(node.cons, scope) else evalExpr(node.alt, scope)
        is MemberExpr -> evalMember(node, scope)
        is CallExpr -> evalCall(node, scope)
        is NewExpr -> evalNew(node, scope)
        else -> Unit
    }

    private fun evalUnary(node: UnaryExpr, scope: Scope): Any? {
        if (node.op == "++" || node.op == "--") {
            val delta = if (node.op == "++") 1.0 else -1.0
            val old = toNumber(evalExpr(node.expr, scope))
            val newVal = old + delta
            assignTo(node.expr, newVal, scope)
            return if (node.prefix) newVal else old
        }
        val v = evalExpr(node.expr, scope)
        return when (node.op) {
            "-" -> -toNumber(v)
            "+" -> toNumber(v)
            "!" -> !toBoolean(v)
            "~" -> toNumber(v).toLong().inv().toDouble()
            else -> Unit
        }
    }

    private fun evalBinary(node: BinExpr, scope: Scope): Any? {
        // Short-circuit
        if (node.op == "&&") {
            val l = evalExpr(node.left, scope)
            return if (!toBoolean(l)) l else evalExpr(node.right, scope)
        }
        if (node.op == "||") {
            val l = evalExpr(node.left, scope)
            return if (toBoolean(l)) l else evalExpr(node.right, scope)
        }
        val l = evalExpr(node.left, scope)
        val r = evalExpr(node.right, scope)
        return when (node.op) {
            "+" -> {
                if (l is String || r is String) toJsString(l) + toJsString(r)
                else if (l is JsList || r is JsList) toJsString(l) + toJsString(r)
                else toNumber(l) + toNumber(r)
            }
            "-" -> toNumber(l) - toNumber(r)
            "*" -> toNumber(l) * toNumber(r)
            "/" -> toNumber(l) / toNumber(r)
            "%" -> toNumber(l) % toNumber(r)
            "<" -> toNumber(l) < toNumber(r)
            "<=" -> toNumber(l) <= toNumber(r)
            ">" -> toNumber(l) > toNumber(r)
            ">=" -> toNumber(l) >= toNumber(r)
            "==" -> looseEq(l, r)
            "!=" -> !looseEq(l, r)
            "===" -> strictEq(l, r)
            "!==" -> !strictEq(l, r)
            "&" -> (toNumber(l).toLong() and toNumber(r).toLong()).toDouble()
            "|" -> (toNumber(l).toLong() or toNumber(r).toLong()).toDouble()
            "^" -> (toNumber(l).toLong() xor toNumber(r).toLong()).toDouble()
            "<<" -> (toNumber(l).toLong() shl toNumber(r).toInt()).toDouble()
            ">>" -> (toNumber(l).toLong() shr toNumber(r).toInt()).toDouble()
            ">>>" -> (toNumber(l).toLong() ushr toNumber(r).toInt()).toDouble()
            "instanceof" -> false
            "in" -> when (r) {
                is JsObject -> toJsString(l) in r.props
                is JsList -> toNumber(l).toInt().let { it >= 0 && it < r.elements.size }
                else -> false
            }
            else -> Unit
        }
    }

    private fun evalAssign(node: AssignExpr, scope: Scope): Any? {
        val right = evalExpr(node.right, scope)
        val value = if (node.op == "=") right else {
            val left = evalExpr(node.left, scope)
            when (node.op) {
                "+=" -> if (left is String || right is String) toJsString(left) + toJsString(right) else toNumber(left) + toNumber(right)
                "-=" -> toNumber(left) - toNumber(right)
                "*=" -> toNumber(left) * toNumber(right)
                "/=" -> toNumber(left) / toNumber(right)
                "%=" -> toNumber(left) % toNumber(right)
                else -> right
            }
        }
        assignTo(node.left, value, scope)
        return value
    }

    private fun assignTo(target: Node, value: Any?, scope: Scope) {
        when (target) {
            is Ident -> scope.set(target.name, value)
            is MemberExpr -> {
                val obj = evalExpr(target.obj, scope)
                val key = if (target.computed) toJsString(evalExpr(target.prop, scope)) else (target.prop as StrLit).v
                when (obj) {
                    is JsObject -> obj.props[key] = value
                    is JsList -> {
                        val idx = key.toIntOrNull()
                        if (idx != null) obj[idx] = value
                        else if (key == "length") obj.length = toNumber(value).toInt()
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }

    private fun evalMember(node: MemberExpr, scope: Scope): Any? {
        val obj = evalExpr(node.obj, scope)
        val key = if (node.computed) toJsString(evalExpr(node.prop, scope)) else (node.prop as StrLit).v
        return getMember(obj, key)
    }

    private fun getMember(obj: Any?, key: String): Any? = when (obj) {
        is JsObject -> obj.props[key] ?: Unit
        is JsList -> when (key) {
            "length" -> obj.length.toDouble()
            "join" -> nativeFn { args -> obj.elements.joinToString(args.getOrNull(0)?.let { toJsString(it) } ?: ",") { toJsString(it) } }
            "reverse" -> nativeFn { _ -> obj.elements.reverse(); obj }
            "push" -> nativeFn { args -> args.forEach { obj.elements.add(it) }; obj.elements.size.toDouble() }
            "pop" -> nativeFn { _ -> if (obj.elements.isEmpty()) Unit else obj.elements.removeAt(obj.elements.size - 1) }
            "shift" -> nativeFn { _ -> if (obj.elements.isEmpty()) Unit else obj.elements.removeAt(0) }
            "unshift" -> nativeFn { args -> args.reversed().forEach { obj.elements.add(0, it) }; obj.elements.size.toDouble() }
            "slice" -> nativeFn { args ->
                val start = args.getOrNull(0)?.let { toNumber(it).toInt() } ?: 0
                val end = args.getOrNull(1)?.let { toNumber(it).toInt() } ?: obj.elements.size
                val s = if (start < 0) maxOf(0, obj.elements.size + start) else minOf(start, obj.elements.size)
                val e = if (end < 0) maxOf(0, obj.elements.size + end) else minOf(end, obj.elements.size)
                JsList(obj.elements.subList(maxOf(0, s), maxOf(s, e)).toMutableList())
            }
            "splice" -> nativeFn { args ->
                val start = args.getOrNull(0)?.let { toNumber(it).toInt() }?.let { if (it < 0) maxOf(0, obj.elements.size + it) else minOf(it, obj.elements.size) } ?: 0
                val deleteCount = args.getOrNull(1)?.let { toNumber(it).toInt() }?.coerceIn(0, obj.elements.size - start) ?: (obj.elements.size - start)
                val removed = JsList(obj.elements.subList(start, start + deleteCount).toMutableList())
                repeat(deleteCount) { obj.elements.removeAt(start) }
                args.drop(2).forEachIndexed { i, v -> obj.elements.add(start + i, v) }
                removed
            }
            "indexOf" -> nativeFn { args ->
                val v = args.getOrNull(0); val start = args.getOrNull(1)?.let { toNumber(it).toInt() } ?: 0
                obj.elements.indexOfFirst { strictEq(it, v) }.let { if (it < start) -1.0 else it.toDouble() }
            }
            "map" -> nativeFn { args ->
                val fn = args.getOrNull(0)
                JsList(obj.elements.mapIndexed { i, v -> callAny(fn, listOf(v, i.toDouble(), obj), null) }.toMutableList())
            }
            "filter" -> nativeFn { args ->
                val fn = args.getOrNull(0)
                JsList(obj.elements.filterIndexed { i, v -> toBoolean(callAny(fn, listOf(v, i.toDouble(), obj), null)) }.toMutableList())
            }
            "forEach" -> nativeFn { args ->
                val fn = args.getOrNull(0)
                obj.elements.forEachIndexed { i, v -> callAny(fn, listOf(v, i.toDouble(), obj), null) }
                Unit
            }
            "reduce" -> nativeFn { args ->
                val fn = args.getOrNull(0)
                var acc: Any? = if (args.size > 1) args[1] else obj.elements.firstOrNull() ?: Unit
                val startIdx = if (args.size > 1) 0 else 1
                for (i in startIdx until obj.elements.size) acc = callAny(fn, listOf(acc, obj.elements[i], i.toDouble(), obj), null)
                acc
            }
            "concat" -> nativeFn { args ->
                val result = JsList(obj.elements.toMutableList())
                args.forEach { a -> when (a) { is JsList -> result.elements.addAll(a.elements); else -> result.elements.add(a) } }
                result
            }
            "find" -> nativeFn { args ->
                val fn = args.getOrNull(0)
                obj.elements.firstOrNull { toBoolean(callAny(fn, listOf(it), null)) } ?: Unit
            }
            "some" -> nativeFn { args ->
                val fn = args.getOrNull(0)
                obj.elements.any { toBoolean(callAny(fn, listOf(it), null)) }
            }
            "every" -> nativeFn { args ->
                val fn = args.getOrNull(0)
                obj.elements.all { toBoolean(callAny(fn, listOf(it), null)) }
            }
            "sort" -> nativeFn { args ->
                val fn = args.getOrNull(0)
                if (fn == null) obj.elements.sortWith { a, b -> toJsString(a).compareTo(toJsString(b)) }
                else obj.elements.sortWith { a, b -> toNumber(callAny(fn, listOf(a, b), null)).toInt() }
                obj
            }
            "includes" -> nativeFn { args -> obj.elements.any { looseEq(it, args.getOrNull(0)) } }
            "toString" -> nativeFn { _ -> obj.elements.joinToString(",") { toJsString(it) } }
            "flat" -> nativeFn { _ ->
                val result = JsList()
                obj.elements.forEach { if (it is JsList) result.elements.addAll(it.elements) else result.elements.add(it) }
                result
            }
            else -> key.toIntOrNull()?.let { obj[it] } ?: Unit
        }
        is String -> when (key) {
            "length" -> obj.length.toDouble()
            "split" -> nativeFn { args ->
                val sep = args.getOrNull(0)
                when {
                    sep == null || sep is Unit -> JsList(mutableListOf(obj))
                    sep is String && sep.isEmpty() -> JsList(obj.map { it.toString() as Any? }.toMutableList())
                    sep is String -> JsList(obj.split(sep).map { it as Any? }.toMutableList())
                    else -> JsList(obj.split(toJsString(sep)).map { it as Any? }.toMutableList())
                }
            }
            "join" -> nativeFn { args -> obj } // strings don't have join but just in case
            "replace" -> nativeFn { args ->
                val from = args.getOrNull(0); val to = toJsString(args.getOrNull(1))
                when (from) {
                    is String -> obj.replaceFirst(from, to)
                    else -> obj.replace(toJsString(from), to)
                }
            }
            "replaceAll" -> nativeFn { args ->
                val from = args.getOrNull(0); val to = toJsString(args.getOrNull(1))
                obj.replace(toJsString(from), to)
            }
            "indexOf" -> nativeFn { args -> obj.indexOf(toJsString(args.getOrNull(0))).toDouble() }
            "lastIndexOf" -> nativeFn { args -> obj.lastIndexOf(toJsString(args.getOrNull(0))).toDouble() }
            "includes" -> nativeFn { args -> obj.contains(toJsString(args.getOrNull(0))) }
            "startsWith" -> nativeFn { args -> obj.startsWith(toJsString(args.getOrNull(0))) }
            "endsWith" -> nativeFn { args -> obj.endsWith(toJsString(args.getOrNull(0))) }
            "slice" -> nativeFn { args ->
                val start = args.getOrNull(0)?.let { toNumber(it).toInt() }?.let { if (it < 0) maxOf(0, obj.length + it) else minOf(it, obj.length) } ?: 0
                val end = args.getOrNull(1)?.let { toNumber(it).toInt() }?.let { if (it < 0) maxOf(0, obj.length + it) else minOf(it, obj.length) } ?: obj.length
                if (end <= start) "" else obj.substring(start, end)
            }
            "substr" -> nativeFn { args ->
                val start = args.getOrNull(0)?.let { toNumber(it).toInt() }?.let { if (it < 0) maxOf(0, obj.length + it) else minOf(it, obj.length) } ?: 0
                val len = args.getOrNull(1)?.let { toNumber(it).toInt() } ?: (obj.length - start)
                if (len <= 0) "" else obj.substring(start, minOf(start + len, obj.length))
            }
            "substring" -> nativeFn { args ->
                val a = args.getOrNull(0)?.let { toNumber(it).toInt().coerceIn(0, obj.length) } ?: 0
                val b = args.getOrNull(1)?.let { toNumber(it).toInt().coerceIn(0, obj.length) } ?: obj.length
                obj.substring(minOf(a, b), maxOf(a, b))
            }
            "charAt" -> nativeFn { args ->
                val i = args.getOrNull(0)?.let { toNumber(it).toInt() } ?: 0
                if (i < 0 || i >= obj.length) "" else obj[i].toString()
            }
            "charCodeAt" -> nativeFn { args ->
                val i = args.getOrNull(0)?.let { toNumber(it).toInt() } ?: 0
                if (i < 0 || i >= obj.length) Double.NaN else obj[i].code.toDouble()
            }
            "codePointAt" -> nativeFn { args ->
                val i = args.getOrNull(0)?.let { toNumber(it).toInt() } ?: 0
                if (i < 0 || i >= obj.length) Double.NaN else obj[i].code.toDouble()
            }
            "toUpperCase", "toLocaleUpperCase" -> nativeFn { _ -> obj.uppercase() }
            "toLowerCase", "toLocaleLowerCase" -> nativeFn { _ -> obj.lowercase() }
            "trim" -> nativeFn { _ -> obj.trim() }
            "trimStart", "trimLeft" -> nativeFn { _ -> obj.trimStart() }
            "trimEnd", "trimRight" -> nativeFn { _ -> obj.trimEnd() }
            "repeat" -> nativeFn { args -> obj.repeat(toNumber(args.getOrNull(0)).toInt().coerceAtLeast(0)) }
            "padStart" -> nativeFn { args ->
                val len = toNumber(args.getOrNull(0)).toInt(); val pad = args.getOrNull(1)?.let { toJsString(it) } ?: " "
                if (obj.length >= len) obj else (pad.repeat(len) + obj).takeLast(len)
            }
            "padEnd" -> nativeFn { args ->
                val len = toNumber(args.getOrNull(0)).toInt(); val pad = args.getOrNull(1)?.let { toJsString(it) } ?: " "
                if (obj.length >= len) obj else (obj + pad.repeat(len)).take(len)
            }
            "toString" -> nativeFn { _ -> obj }
            "valueOf" -> nativeFn { _ -> obj }
            "match" -> nativeFn { args ->
                val pattern = toJsString(args.getOrNull(0))
                try {
                    val result = Regex(pattern).find(obj)
                    if (result == null) null
                    else JsList(result.groupValues.map { it as Any? }.toMutableList())
                } catch (_: Exception) { null }
            }
            else -> key.toIntOrNull()?.let {
                if (it >= 0 && it < obj.length) obj[it].toString() else Unit 
            } ?: Unit
        }
        is Double -> when (key) {
            "toString" -> nativeFn { args ->
                val radix = args.getOrNull(0)?.let { toNumber(it).toInt() } ?: 10
                if (radix == 10) toJsString(obj) else obj.toLong().toString(radix)
            }
            "toFixed" -> nativeFn { args ->
                val digits = args.getOrNull(0)?.let { toNumber(it).toInt() } ?: 0
                "%.${digits}f".format(obj)
            }
            else -> Unit
        }
        else -> Unit
    }

    private fun evalCall(node: CallExpr, scope: Scope): Any? {
        val (callee, thisVal) = resolveCallee(node.callee, scope)
        val args = node.args.map { evalExpr(it, scope) }
        return callAny(callee, args, thisVal)
    }

    private fun resolveCallee(calleeNode: Node, scope: Scope): Pair<Any?, Any?> {
        return if (calleeNode is MemberExpr) {
            val obj = evalExpr(calleeNode.obj, scope)
            val key = if (calleeNode.computed) toJsString(evalExpr(calleeNode.prop, scope)) else (calleeNode.prop as StrLit).v
            getMember(obj, key) to obj
        } else {
            evalExpr(calleeNode, scope) to null
        }
    }

    private fun evalNew(node: NewExpr, scope: Scope): Any? {
        val callee = evalExpr(node.callee, scope)
        val args = node.args.map { evalExpr(it, scope) }
        // Just call it
        return callAny(callee, args, JsObject())
    }

    private fun callAny(callee: Any?, args: List<Any?>, thisVal: Any?): Any? = when (callee) {
        is NativeFn -> callee.fn(args)
        is JsFunction -> {
            val fnScope = Scope(callee.closure)
            fnScope.define("this", thisVal)
            fnScope.define("arguments", JsList(args.toMutableList()))
            callee.params.forEachIndexed { i, p -> fnScope.define(p, args.getOrElse(i) { Unit }) }
            try {
                var last: Any? = Unit
                for (s in callee.body) last = execNode(s, fnScope)
                last
            } catch (r: ReturnSignal) {
                r.value
            }
        }
        else -> Unit
    }
}

// Wrapper so we can store Kotlin lambdas as "callable" values
private class NativeFn(val fn: (List<Any?>) -> Any?)
