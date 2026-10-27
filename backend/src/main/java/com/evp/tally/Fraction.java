package com.evp.tally;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

public final class Fraction implements Comparable<Fraction> {

    public static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);
    public static final Fraction ONE = new Fraction(BigInteger.ONE, BigInteger.ONE);

    private final BigInteger numerator;
    private final BigInteger denominator;

    private Fraction(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (gcd.signum() != 0 && !gcd.equals(BigInteger.ONE)) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static Fraction of(long whole) {
        return new Fraction(BigInteger.valueOf(whole), BigInteger.ONE);
    }

    public Fraction plus(Fraction o) {
        return new Fraction(numerator.multiply(o.denominator).add(o.numerator.multiply(denominator)),
                denominator.multiply(o.denominator));
    }

    public Fraction minus(Fraction o) {
        return plus(new Fraction(o.numerator.negate(), o.denominator));
    }

    public Fraction times(Fraction o) {
        return new Fraction(numerator.multiply(o.numerator), denominator.multiply(o.denominator));
    }

    public Fraction dividedBy(Fraction o) {
        return new Fraction(numerator.multiply(o.denominator), denominator.multiply(o.numerator));
    }

    public int signum() {
        return numerator.signum();
    }

    public double display() {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), MathContext.DECIMAL64)
                .setScale(4, RoundingMode.HALF_EVEN).doubleValue();
    }

    @Override
    public int compareTo(Fraction o) {
        return numerator.multiply(o.denominator).compareTo(o.numerator.multiply(denominator));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Fraction f && numerator.equals(f.numerator) && denominator.equals(f.denominator);
    }

    @Override
    public int hashCode() {
        return 31 * numerator.hashCode() + denominator.hashCode();
    }

    @Override
    public String toString() {
        return denominator.equals(BigInteger.ONE) ? numerator.toString() : numerator + "/" + denominator;
    }
}