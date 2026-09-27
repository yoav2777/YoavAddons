package com.yoav3577.bazaaranalyzer.core;

@FunctionalInterface
public interface PriceBook {
   double price(String id);
}
