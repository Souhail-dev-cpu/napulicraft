package dev.napuli.client;

import java.math.BigDecimal;
import java.math.MathContext;

/** Small bounded arithmetic parser; never evaluates code or accesses a scripting engine. */
final class PhoneCalculator {
    private final String value;
    private int cursor;
    private PhoneCalculator(String value){this.value=value;}
    static String calculate(String expression){
        if(expression.isBlank()||expression.length()>64)throw new IllegalArgumentException();
        var parser=new PhoneCalculator(expression.replace('×','*').replace('÷','/').replace('−','-').replace(',','.').replace(" ",""));
        var result=parser.sum().stripTrailingZeros();if(parser.cursor!=parser.value.length()||result.precision()>24||Math.abs(result.scale())>24)throw new IllegalArgumentException();return result.toPlainString();
    }
    private BigDecimal sum(){var left=product();while(cursor<value.length()&&(value.charAt(cursor)=='+'||value.charAt(cursor)=='-')){char op=value.charAt(cursor++);var right=product();left=op=='+'?left.add(right,MathContext.DECIMAL64):left.subtract(right,MathContext.DECIMAL64);}return left;}
    private BigDecimal product(){var left=number();while(cursor<value.length()&&(value.charAt(cursor)=='*'||value.charAt(cursor)=='/')){char op=value.charAt(cursor++);var right=number();left=op=='*'?left.multiply(right,MathContext.DECIMAL64):left.divide(right,MathContext.DECIMAL64);}return left;}
    private BigDecimal number(){
        boolean negative=false;while(cursor<value.length()&&(value.charAt(cursor)=='-'||value.charAt(cursor)=='+'))if(value.charAt(cursor++)=='-')negative=!negative;
        int start=cursor;while(cursor<value.length()&&(Character.isDigit(value.charAt(cursor))||value.charAt(cursor)=='.'))cursor++;if(start==cursor)throw new IllegalArgumentException();
        var result=new BigDecimal(value.substring(start,cursor));if(negative)result=result.negate();while(cursor<value.length()&&value.charAt(cursor)=='%'){cursor++;result=result.movePointLeft(2);}return result;
    }
}
