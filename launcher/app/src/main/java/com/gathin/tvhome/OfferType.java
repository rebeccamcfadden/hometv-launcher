package com.gathin.tvhome;

/**
 * How a service offers a title (design_preview/src/types.ts, OfferType): SUB is included with a subscription, so it
 * counts as included only when the household has that service; FREE is always included; RENT and BUY have a price.
 */
enum OfferType { SUB, FREE, RENT, BUY }
