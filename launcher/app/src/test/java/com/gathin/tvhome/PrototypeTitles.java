package com.gathin.tvhome;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The prototype's outputs for every title in the mock catalogue, in src/test/resources/prototype_titles.tsv:
 * design_preview/src/data/offers.ts run as it is, with titleMeta and provenance from ui/widgets.ts and every
 * service the prototype marks subscribed. Made with Node 22+ by a throwaway script that imports catalog.ts and
 * offers.ts (node --experimental-strip-types, with a resolve hook that adds the .ts the prototype's imports leave
 * out) and writes one tab-separated line per title, in the columns of its header. Regenerate it the same way if the
 * prototype's data changes.
 */
final class PrototypeTitles {
    private PrototypeTitles() {}

    static final class Row {
        String id, bestOffer, offerLabel, availability, meta, provenance, kindYear, byService;
        boolean included;
    }

    static List<Row> rows() throws Exception {
        List<Row> out = new ArrayList<>();
        try (InputStream in = PrototypeTitles.class.getResourceAsStream("/prototype_titles.tsv");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                Row row = new Row();
                row.id = f[0];
                row.included = Boolean.parseBoolean(f[1]);
                row.bestOffer = f[2];
                row.offerLabel = f[3];
                row.availability = f[4];
                row.meta = f[5];
                row.provenance = f[6];
                row.kindYear = f[7];
                row.byService = f[8];
                out.add(row);
            }
        }
        return out;
    }
}
