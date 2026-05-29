package com.lagradost.cloudstream3.utils

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JsInterpreterTest {

    private fun bool(code: String, variable: String? = null) = evalJs(code, variable) as? Boolean ?: false
    private fun num(code: String, variable: String? = null) = evalJs(code, variable) as? Double ?: Double.NaN
    private fun str(code: String, variable: String? = null) = jsValueToString(evalJs(code, variable))

    private fun assertApprox(expected: Double, actual: Double, tol: Double = 1e-9) {
        assertTrue(abs(actual - expected) <= tol, "Expected $expected ± $tol but was $actual")
    }

    @Test
    fun integerLiteral() {
        assertEquals(42.0, num("42"))
    }

    @Test
    fun negativeLiteral() {
        assertEquals(-7.0, num("-7"))
    }

    @Test
    fun floatLiteral() {
        assertApprox(3.14, num("3.14"))
    }

    @Test
    fun hexLiteral() {
        assertEquals(255.0, num("0xff"))
    }

    @Test
    fun stringLiteralDouble() {
        assertEquals("hi", str("\"hi\""))
    }

    @Test
    fun stringLiteralSingle() {
        assertEquals("hi", str("'hi'"))
    }

    @Test
    fun templateLiteral() {
        assertEquals("hi", str("`hi`"))
    }

    @Test
    fun booleanTrue() {
        assertTrue(bool("true"))
    }

    @Test
    fun booleanFalse() {
        assertFalse(bool("false"))
    }

    @Test
    fun nullLiteral() {
        assertNull(evalJs("null"))
    }

    @Test
    fun undefinedLiteral() {
        assertEquals(Unit, evalJs("undefined"))
    }

    @Test
    fun addition() {
        assertEquals(5.0, num("2+3"))
    }

    @Test
    fun subtraction() {
        assertEquals(1.0, num("3-2"))
    }

    @Test
    fun multiplication() {
        assertEquals(6.0, num("2*3"))
    }

    @Test
    fun division() {
        assertEquals(2.5, num("5/2"))
    }

    @Test
    fun modulo() {
        assertEquals(1.0, num("7%3"))
    }

    @Test
    fun operatorPrecedence() {
        assertEquals(7.0, num("1+2*3"))
    }

    @Test
    fun parenthesesOverride() {
        assertEquals(9.0, num("(1+2)*3"))
    }

    @Test
    fun unaryMinus() {
        assertEquals(-5.0, num("-(2+3)"))
    }

    @Test
    fun unaryPlusCoerces() {
        assertEquals(3.0, num("+'3'"))
    }

    @Test
    fun bitwiseAnd() {
        assertEquals(2.0, num("6&3"))
    }

    @Test
    fun bitwiseOr() {
        assertEquals(7.0, num("5|3"))
    }

    @Test
    fun bitwiseXor() {
        assertEquals(6.0, num("5^3"))
    }

    @Test
    fun bitwiseNot() {
        assertEquals(-6.0, num("~5"))
    }

    @Test
    fun leftShift() {
        assertEquals(20.0, num("5<<2"))
    }

    @Test
    fun rightShift() {
        assertEquals(1.0, num("5>>2"))
    }

    @Test
    fun unsignedRightShift() {
        assertEquals(1.0, num("5>>>2"))
    }

    @Test
    fun lessThanTrue() {
        assertTrue(bool("1<2"))
    }

    @Test
    fun lessThanFalse() {
        assertFalse(bool("2<1"))
    }

    @Test
    fun looseEqNumberString() {
        assertTrue(bool("1=='1'"))
    }

    @Test
    fun strictEqSameType() {
        assertTrue(bool("1===1"))
    }

    @Test
    fun strictEqDifferentType() {
        assertFalse(bool("1==='1'"))
    }

    @Test
    fun strictNeq() {
        assertTrue(bool("1!=='1'"))
    }

    @Test
    fun logicalAndRightWhenLeftTruthy() {
        assertEquals(2.0, num("1&&2"))
    }

    @Test
    fun logicalAndLeftWhenLeftFalsy() {
        assertEquals(0.0, num("0&&2"))
    }

    @Test
    fun logicalOrLeftWhenTruthy() {
        assertEquals(1.0, num("1||2"))
    }

    @Test
    fun logicalOrRightWhenLeftFalsy() {
        assertEquals(2.0, num("0||2"))
    }

    @Test
    fun logicalNot() {
        assertTrue(bool("!false"))
    }

    @Test
    fun ternaryTrueBranch() {
        assertEquals(1.0, num("true?1:2"))
    }

    @Test
    fun ternaryFalseBranch() {
        assertEquals(2.0, num("false?1:2"))
    }

    @Test
    fun varDeclarationAndRead() {
        assertEquals(10.0, num("var x=10; x"))
    }

    @Test
    fun letDeclaration() {
        assertEquals("hello", str("let s='hello'; s"))
    }

    @Test
    fun constDeclaration() {
        assertApprox(3.14, num("const PI=3.14; PI"))
    }

    @Test
    fun assignmentPlusEquals() {
        assertEquals(15.0, num("var x=10; x+=5; x"))
    }

    @Test
    fun assignmentMinusEquals() {
        assertEquals(5.0, num("var x=10; x-=5; x"))
    }

    @Test
    fun assignmentTimesEquals() {
        assertEquals(20.0, num("var x=4; x*=5; x"))
    }

    @Test
    fun prefixIncrement() {
        assertEquals(6.0, num("var x=5; ++x"))
    }

    @Test
    fun postfixIncrementReturnsOldValue() {
        assertEquals(5.0, num("var x=5; x++"))
    }

    @Test
    fun postfixIncrementMutatesVariable() {
        assertEquals(6.0, num("var x=5; x++; x"))
    }

    @Test
    fun typeofNumber() {
        assertEquals("number", str("typeof 42"))
    }

    @Test
    fun typeofString() {
        assertEquals("string", str("typeof 'x'"))
    }

    @Test
    fun typeofBoolean() {
        assertEquals("boolean", str("typeof true"))
    }

    @Test
    fun typeofUndefined() {
        assertEquals("undefined", str("typeof undefined"))
    }

    @Test
    fun typeofFunction() {
        assertEquals("function", str("typeof function(){}"))
    }

    @Test
    fun stringConcatenation() {
        assertEquals("ab", str("'a'+'b'"))
    }

    @Test
    fun numberPlusStringCoercesToString() {
        assertEquals("1x", str("1+'x'"))
    }

    @Test
    fun stringLength() {
        assertEquals(5.0, num("'hello'.length"))
    }

    @Test
    fun stringCharAt() {
        assertEquals("e", str("'hello'.charAt(1)"))
    }

    @Test
    fun stringCharCodeAt() {
        assertEquals(104.0, num("'hello'.charCodeAt(0)"))
    }

    @Test
    fun stringIndexOfFound() {
        assertEquals(1.0, num("'hello'.indexOf('e')"))
    }

    @Test
    fun stringIndexOfNotFound() {
        assertEquals(-1.0, num("'hello'.indexOf('z')"))
    }

    @Test
    fun stringSlice() {
        assertEquals("ell", str("'hello'.slice(1,4)"))
    }

    @Test
    fun stringSliceNegativeIndex() {
        assertEquals("lo", str("'hello'.slice(-2)"))
    }

    @Test
    fun stringSubstr() {
        assertEquals("ell", str("'hello'.substr(1,3)"))
    }

    @Test
    fun stringSubstring() {
        assertEquals("ell", str("'hello'.substring(1,4)"))
    }

    @Test
    fun stringSplitAndJoin() {
        assertEquals("a-b-c", str("'a|b|c'.split('|').join('-')"))
    }

    @Test
    fun stringSplitEmptySepGivesChars() {
        assertEquals("h,e,l,l,o", str("'hello'.split('').join(',')"))
    }

    @Test
    fun stringReplaceFirstOccurrence() {
        assertEquals("xbc", str("'abc'.replace('a','x')"))
    }

    @Test
    fun stringToUpperCase() {
        assertEquals("HELLO", str("'hello'.toUpperCase()"))
    }

    @Test
    fun stringToLowerCase() {
        assertEquals("hello", str("'HELLO'.toLowerCase()"))
    }

    @Test
    fun stringTrim() {
        assertEquals("hi", str("'  hi  '.trim()"))
    }

    @Test
    fun stringRepeat() {
        assertEquals("aaa", str("'a'.repeat(3)"))
    }

    @Test
    fun stringPadStart() {
        assertEquals("005", str("'5'.padStart(3,'0')"))
    }

    @Test
    fun stringPadEnd() {
        assertEquals("500", str("'5'.padEnd(3,'0')"))
    }

    @Test
    fun stringIncludes() {
        assertTrue(bool("'hello'.includes('ell')"))
    }

    @Test
    fun stringStartsWith() {
        assertTrue(bool("'hello'.startsWith('hel')"))
    }

    @Test
    fun stringEndsWith() {
        assertTrue(bool("'hello'.endsWith('llo')"))
    }

    @Test
    fun stringFromCharCode() {
        assertEquals("A", str("String.fromCharCode(65)"))
    }

    @Test
    fun stringFromCharCodeMultiple() {
        assertEquals("Hi", str("String.fromCharCode(72,105)"))
    }

    @Test
    fun numberToStringRadix16() {
        assertEquals("ff", str("(255).toString(16)"))
    }

    @Test
    fun numberToFixed() {
        assertEquals("3.14", str("(3.14159).toFixed(2)"))
    }

    @Test
    fun toFixedZeroDigits() {
        assertEquals("4", str("(3.6).toFixed(0)"))
    }

    @Test
    fun toFixedTwoDigits() {
        assertEquals("3.14", str("(3.14159).toFixed(2)"))
    }

    @Test
    fun toFixedPadsWithZeroes() {
        assertEquals("3.10", str("(3.1).toFixed(2)"))
    }

    @Test
    fun toFixedNegativeNumber() {
        assertEquals("-3.14", str("(-3.14159).toFixed(2)"))
    }

    @Test
    fun toFixedNegativeBetweenZeroAndMinusOne() {
        assertEquals("-0.50", str("(-0.5).toFixed(2)"))
    }

    @Test
    fun toFixedWholeNumber() {
        assertEquals("5.00", str("(5).toFixed(2)"))
    }

    @Test
    fun toFixedZeroValue() {
        assertEquals("0.00", str("(0).toFixed(2)"))
    }

    @Test
    fun arrayLiteralAndLength() {
        assertEquals(3.0, num("[1,2,3].length"))
    }

    @Test
    fun arrayIndexAccess() {
        assertEquals(2.0, num("[1,2,3][1]"))
    }

    @Test
    fun arrayJoin() {
        assertEquals("1,2,3", str("[1,2,3].join(',')"))
    }

    @Test
    fun arrayReverse() {
        assertEquals("3,2,1", str("[1,2,3].reverse().join(',')"))
    }

    @Test
    fun arrayPushReturnsNewLength() {
        assertEquals(4.0, num("var a=[1,2,3]; a.push(4)"))
    }

    @Test
    fun arrayPopRemovesLastElement() {
        assertEquals(3.0, num("var a=[1,2,3]; a.pop()"))
    }

    @Test
    fun arraySlice() {
        assertEquals("2,3", str("[1,2,3,4].slice(1,3).join(',')"))
    }

    @Test
    fun arrayMap() {
        assertEquals("2,4,6", str("[1,2,3].map(function(x){return x*2}).join(',')"))
    }

    @Test
    fun arrayFilter() {
        assertEquals("2,4", str("[1,2,3,4].filter(function(x){return x%2===0}).join(',')"))
    }

    @Test
    fun arrayReduce() {
        assertEquals(10.0, num("[1,2,3,4].reduce(function(acc,x){return acc+x},0)"))
    }

    @Test
    fun arrayIndexOf() {
        assertEquals(2.0, num("[10,20,30].indexOf(30)"))
    }

    @Test
    fun arrayIncludes() {
        assertTrue(bool("[1,2,3].includes(2)"))
    }

    @Test
    fun arrayConcat() {
        assertEquals("1,2,3,4", str("[1,2].concat([3,4]).join(',')"))
    }

    @Test
    fun arraySome() {
        assertTrue(bool("[1,2,3].some(function(x){return x>2})"))
    }

    @Test
    fun arrayEvery() {
        assertFalse(bool("[1,2,3].every(function(x){return x>2})"))
    }

    @Test
    fun arraySortWithComparator() {
        assertEquals("1,2,10", str("[10,1,2].sort(function(a,b){return a-b}).join(',')"))
    }

    @Test
    fun objectPropertyAccessWithDot() {
        assertEquals(1.0, num("var o={a:1}; o.a"))
    }

    @Test
    fun objectPropertyAccessWithBracket() {
        assertEquals(2.0, num("var o={b:2}; o['b']"))
    }

    @Test
    fun objectPropertyAssignment() {
        assertEquals(99.0, num("var o={}; o.x=99; o.x"))
    }

    @Test
    fun ifTrueBranch() {
        assertEquals(1.0, num("var r=0; if(true){r=1} r"))
    }

    @Test
    fun ifFalseUsesElse() {
        assertEquals(2.0, num("var r=0; if(false){r=1}else{r=2} r"))
    }

    @Test
    fun whileLoop() {
        assertEquals(10.0, num("var i=0; while(i<10){i++} i"))
    }

    @Test
    fun whileBreak() {
        assertEquals(5.0, num("var i=0; while(true){if(i===5)break; i++} i"))
    }

    @Test
    fun whileContinue() {
        assertEquals(25.0, num("var i=0; var s=0; while(i<10){i++; if(i%2===0)continue; s+=i} s"))
    }

    @Test
    fun forLoop() {
        assertEquals(10.0, num("var s=0; for(var i=1;i<=4;i++){s+=i} s"))
    }

    @Test
    fun forLoopWithBreak() {
        assertEquals(3.0, num("var i; for(i=0;i<10;i++){if(i===3)break} i"))
    }

    @Test
    fun forInOverObjectKeys() {
        assertEquals("a,b,c", str("var o={a:1,b:2,c:3}; var keys=[]; for(var k in o){keys.push(k)} keys.sort().join(',')"))
    }

    @Test
    fun namedFunctionDeclarationAndCall() {
        assertEquals(7.0, num("function add(a,b){return a+b} add(3,4)"))
    }

    @Test
    fun anonymousFunctionExpression() {
        assertEquals(12.0, num("var mul=function(a,b){return a*b}; mul(3,4)"))
    }

    @Test
    fun recursiveFunction() {
        assertEquals(120.0, num("function fact(n){if(n<=1)return 1; return n*fact(n-1)} fact(5)"))
    }

    @Test
    fun closureCapturesOuterVariable() {
        assertEquals(3.0, num("var c=0; function inc(){c+=1} inc();inc();inc(); c"))
    }

    @Test
    fun immediatelyInvokedFunctionExpression() {
        assertEquals(9.0, num("(function(x){return x*x})(3)"))
    }

    @Test
    fun functionAsArgument() {
        assertEquals(6.0, num("function apply(f,x){return f(x)} apply(function(n){return n+1},5)"))
    }

    @Test
    fun mathFloor() {
        assertEquals(3.0, num("Math.floor(3.9)"))
    }

    @Test
    fun mathCeil() {
        assertEquals(4.0, num("Math.ceil(3.1)"))
    }

    @Test
    fun mathRound() {
        assertEquals(4.0, num("Math.round(3.6)"))
    }

    @Test
    fun mathAbs() {
        assertEquals(5.0, num("Math.abs(-5)"))
    }

    @Test
    fun mathSqrt() {
        assertApprox(3.0, num("Math.sqrt(9)"))
    }

    @Test
    fun mathPow() {
        assertEquals(8.0, num("Math.pow(2,3)"))
    }

    @Test
    fun mathMax() {
        assertEquals(9.0, num("Math.max(1,9,3)"))
    }

    @Test
    fun mathMin() {
        assertEquals(1.0, num("Math.min(1,9,3)"))
    }

    @Test
    fun mathPi() {
        assertApprox(PI, num("Math.PI"))
    }

    @Test
    fun mathE() {
        assertApprox(E, num("Math.E"))
    }

    @Test
    fun mathLog() {
        assertApprox(0.0, num("Math.log(1)"))
    }

    @Test
    fun mathTruncPositive() {
        assertEquals(3.0, num("Math.trunc(3.9)"))
    }

    @Test
    fun mathTruncNegative() {
        assertEquals(-3.0, num("Math.trunc(-3.9)"))
    }

    @Test
    fun mathTruncZero() {
        assertEquals(0.0, num("Math.trunc(0.5)"))
    }

    @Test
    fun mathRandomInRange() {
        val r = num("Math.random()")
        assertTrue(r >= 0.0 && r < 1.0, "Math.random() should be in [0,1) but was $r")
    }

    @Test
    fun mathRandomProducesDifferentValues() {
        val results = (1..20).map { num("Math.random()") }.toSet()
        assertTrue(results.size > 1, "Math.random() produced identical values across 20 calls")
    }

    @Test
    fun parseIntDecimal() {
        assertEquals(42.0, num("parseInt('42')"))
    }

    @Test
    fun parseIntHex() {
        assertEquals(255.0, num("parseInt('ff',16)"))
    }

    @Test
    fun parseIntBinary() {
        assertEquals(5.0, num("parseInt('101',2)"))
    }

    @Test
    fun parseIntInvalid() {
        assertTrue(num("parseInt('abc')").isNaN())
    }

    @Test
    fun parseFloat() {
        assertApprox(3.14, num("parseFloat('3.14')"))
    }

    @Test
    fun isNanTrue() {
        assertTrue(bool("isNaN(NaN)"))
    }

    @Test
    fun isNanFalse() {
        assertFalse(bool("isNaN(1)"))
    }

    @Test
    fun isFiniteFalse() {
        assertFalse(bool("isFinite(Infinity)"))
    }

    @Test
    fun isFiniteTrue() {
        assertTrue(bool("isFinite(1)"))
    }

    @Test
    fun tryCatchSwallowsThrownValue() {
        assertEquals(42.0, num("var r=0; try{throw 42}catch(e){r=e} r"))
    }

    @Test
    fun finallyAlwaysRuns() {
        assertEquals(99.0, num("var r=0; try{throw 1}catch(e){}finally{r=99} r"))
    }

    @Test
    fun tryWithoutThrowSkipsCatch() {
        assertEquals(1.0, num("var r=0; try{r=1}catch(e){r=99} r"))
    }

    @Test
    fun jsContextPersistsVariablesAcrossEvals() {
        val ctx = JsContext()
        ctx.eval("var x = 10")
        ctx.eval("x += 5")
        assertEquals(15.0, ctx["x"] as? Double ?: 0.0)
    }

    @Test
    fun jsContextGetReturnsNullForUndefined() {
        val ctx = JsContext()
        assertNull(ctx["nope"])
    }

    @Test
    fun jsContextSetExposesValueToEval() {
        val ctx = JsContext()
        ctx["base"] = 100.0
        ctx.eval("var result = base + 1")
        assertEquals(101.0, ctx["result"] as? Double ?: 0.0)
    }

    @Test
    fun jsContextEvalReturnsLastExpression() {
        val ctx = JsContext()
        val result = ctx.eval("1+2")
        assertEquals(3.0, result as? Double ?: 0.0)
    }

    @Test
    fun jsContextUrlExtractionPattern() {
        val scriptContent = "var url = '/e/abc123?t=' + (1000+337) + '&s=xyz'"
        val ctx = JsContext()
        ctx.eval(scriptContent)
        assertEquals("/e/abc123?t=1337&s=xyz", ctx["url"]?.toString())
    }

    @Test
    fun evaluateMathSimpleAddition() {
        assertEquals("5", jsValueToString(evalJs("eval(2+3)")))
    }

    @Test
    fun evaluateMathNestedParens() {
        assertEquals("12", jsValueToString(evalJs("eval((2+4)*2)")))
    }

    @Test
    fun evaluateMathProducesCharCode() {
        val code = "eval(1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1)"
        assertEquals(65.0, (evalJs(code) as? Double) ?: 0.0)
    }

    @Test
    fun evalJsWithVariableReturnsNamedVar() {
        assertEquals(42.0, num("var x = 42", "x"))
    }

    @Test
    fun evalJsWithVariableAfterComputation() {
        assertEquals(7.0, num("var x = 1 + 2 * 3", "x"))
    }

    @Test
    fun evalJsWithVariableStringValue() {
        assertEquals("https://example.com", str("var url = 'https://example.com'", "url"))
    }

    @Test
    fun evalJsWithVariableNullValue() {
        assertNull(evalJs("var x = null", "x"))
    }

    @Test
    fun evalJsWithVariableReturnsNullForUndefined() {
        assertNull(evalJs("var x = 42", "y"))
    }

    @Test
    fun evalJsWithVariableUnitWhenNoVariable() {
        assertEquals(Unit, evalJs("var x = 42"))
    }

    @Test
    fun evalJsWithVariableAfterMultipleStatements() {
        assertEquals(15.0, num("var x = 0; for(var i=1;i<=5;i++){x+=i}", "x"))
    }

    @Test
    fun hexEncodedStringDecoding() {
        val code = """
            var encoded = '48|65|6c|6c|6f';
            var decoded = encoded.split('|').map(function(h){
                return String.fromCharCode(parseInt(h, 16));
            }).join('');
            decoded
        """.trimIndent()
        assertEquals("Hello", str(code))
    }

    @Test
    fun charCodeArrayToString() {
        val code = """
            var codes = [72, 101, 108, 108, 111];
            var s = '';
            for(var i=0; i<codes.length; i++){
                s += String.fromCharCode(codes[i]);
            }
            s
        """.trimIndent()
        assertEquals("Hello", str(code))
    }

    @Test
    fun stringReversePattern() {
        assertEquals("hello world", str("'dlrow olleh'.split('').reverse().join('')"))
    }

    @Test
    fun baseConversionLookupTable() {
        val code = """
            var alpha = '0123456789abcdef';
            function toBase16(n) {
                var r = '';
                while(n > 0) {
                    r = alpha[n % 16] + r;
                    n = Math.floor(n / 16);
                }
                return r || '0';
            }
            toBase16(255)
        """.trimIndent()
        assertEquals("ff", str(code))
    }

    @Test
    fun xorDeobfuscation() {
        val code = """
            var _0x1 = function(s) {
                return s.split('').map(function(c) {
                    return String.fromCharCode(c.charCodeAt(0) ^ 1);
                }).join('');
            };
            _0x1('ifmmp')
        """.trimIndent()
        val expected = "ifmmp".map { (it.code xor 1).toChar() }.joinToString("")
        assertEquals(expected, str(code))
    }

    @Test
    fun symtabLookupPattern() {
        val code = """
            var symtab = ['hello', '', 'world', 'foo'];
            var tokens = '0 2'.split(' ');
            var result = tokens.map(function(w){
                var idx = parseInt(w,10);
                var v = symtab[idx];
                return (v !== undefined && v !== '') ? v : w;
            }).join(' ');
            result
        """.trimIndent()
        assertEquals("hello world", str(code))
    }

    @Test
    fun hunterDecoderDufHelper() {
        val code = """
            function duf(d, e) {
                var str = '0123456789abcdefghijklmnopqrstuvwxyz';
                var h = str.substring(0, e);
                var j = 0.0;
                var rev = d.split('').reverse().join('');
                for(var c=0; c<rev.length; c++){
                    var idx = h.indexOf(rev.charAt(c));
                    if(idx >= 0) j += idx * Math.pow(e, c);
                }
                return Math.floor(j);
            }
            duf('z', 36)
        """.trimIndent()
        assertEquals(35.0, num(code))
    }
}
