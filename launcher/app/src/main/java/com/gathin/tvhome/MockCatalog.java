package com.gathin.tvhome;

import static com.gathin.tvhome.ListId.FRIENDS;
import static com.gathin.tvhome.ListId.HOUSEHOLD;
import static com.gathin.tvhome.ListId.MY;
import static com.gathin.tvhome.ServiceId.APPLETV;
import static com.gathin.tvhome.ServiceId.APPLETVPLUS;
import static com.gathin.tvhome.ServiceId.DISNEY;
import static com.gathin.tvhome.ServiceId.GOOGLETV;
import static com.gathin.tvhome.ServiceId.HULU;
import static com.gathin.tvhome.ServiceId.MAX;
import static com.gathin.tvhome.ServiceId.NETFLIX;
import static com.gathin.tvhome.ServiceId.PRIME;
import static com.gathin.tvhome.ServiceId.YOUTUBE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The mock catalogue, ported verbatim from design_preview/src/data/catalog.ts: every title, offer, person and season,
 * in the same order and with the same helpers (sub, free, rent, buy, stores, seasons). Illustrative data only:
 * availability, prices and scores are made up (BACKLOG.md, Real catalogue). Pure Java, so it is unit-tested.
 */
final class MockCatalog implements Catalog {

    private final List<Title> titles;
    private final Map<String, Title> byId = new HashMap<>();
    private final List<String> genres;

    MockCatalog() {
        titles = Collections.unmodifiableList(build());
        TreeSet<String> g = new TreeSet<>();
        for (Title t : titles) {
            byId.put(t.id, t);
            g.addAll(t.genres);
        }
        genres = Collections.unmodifiableList(new ArrayList<>(g));
    }

    @Override
    public List<Title> all() {
        return titles;
    }

    @Override
    public Title get(String id) {
        return id == null ? null : byId.get(id);
    }

    @Override
    public List<String> genres() {
        return genres;
    }

    // ---- The prototype's helpers -------------------------------------------------------------------------------

    private static Offer sub(ServiceId s) {
        return new Offer(s, OfferType.SUB, 0, "4K · HDR");
    }

    private static Offer free(ServiceId s) {
        return new Offer(s, OfferType.FREE, 0, "HD");
    }

    private static Offer rent(ServiceId s, double price, String quality) {
        return new Offer(s, OfferType.RENT, price, quality);
    }

    private static Offer buy(ServiceId s, double price, String quality) {
        return new Offer(s, OfferType.BUY, price, quality);
    }

    /** Rent and buy offers in the three stores. */
    private static Offer[] stores(double r, double b) {
        return new Offer[]{
                rent(APPLETV, r, "4K · Dolby Vision"),
                buy(APPLETV, b, "4K · Dolby Vision"),
                rent(GOOGLETV, r, "4K"),
                buy(GOOGLETV, b + 3, "4K"),
                rent(YOUTUBE, r, "HD"),
                buy(YOUTUBE, b, "HD"),
        };
    }

    private static Offer[] stores() {
        return stores(3.99, 9.99);
    }

    private static Person p(String name, String role) {
        return new Person(name, role);
    }

    /**
     * Seasons from a spec: an Integer is a season of that many "Episode n", a String[] names its episodes. Episode
     * runtimes vary around {@code runtime} as the prototype's do.
     */
    private static List<Season> seasons(int runtime, Object... spec) {
        List<Season> out = new ArrayList<>();
        for (int i = 0; i < spec.length; i++) {
            List<String> names = new ArrayList<>();
            if (spec[i] instanceof Integer) {
                int n = (Integer) spec[i];
                for (int k = 0; k < n; k++) names.add("Episode " + (k + 1));
            } else {
                names.addAll(Arrays.asList((String[]) spec[i]));
            }
            List<Episode> episodes = new ArrayList<>();
            for (int k = 0; k < names.size(); k++) {
                episodes.add(new Episode(k + 1, names.get(k), runtime + ((k * 7) % 9) - 4));
            }
            out.add(new Season(i + 1, episodes));
        }
        return out;
    }

    private static B movie(String id, String name, int year, int runtime) {
        return new B(id, Kind.MOVIE, name, year, runtime);
    }

    private static B show(String id, String name, int year) {
        return new B(id, Kind.SHOW, name, year, 0);
    }

    /** Builds one title the way the prototype's object literals read. */
    private static final class B {
        private final String id;
        private final Kind kind;
        private final String name;
        private final int year;
        private final int runtime;
        private List<String> genres = Collections.emptyList();
        private String rating = "";
        private double score;
        private String synopsis = "";
        private String art = "";
        private final List<Offer> offers = new ArrayList<>();
        private final List<ListId> lists = new ArrayList<>();
        private final List<Person> cast = new ArrayList<>();
        private final List<Person> crew = new ArrayList<>();
        private List<Season> seasons = Collections.emptyList();
        private int nextSeason, nextEpisode;

        B(String id, Kind kind, String name, int year, int runtime) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.year = year;
            this.runtime = runtime;
        }

        B genres(String... g) {
            genres = Arrays.asList(g);
            return this;
        }

        B rated(String rating, double score) {
            this.rating = rating;
            this.score = score;
            return this;
        }

        B synopsis(String s) {
            synopsis = s;
            return this;
        }

        B art(String a) {
            art = a;
            return this;
        }

        B offers(Offer... o) {
            offers.addAll(Arrays.asList(o));
            return this;
        }

        B lists(ListId... l) {
            lists.addAll(Arrays.asList(l));
            return this;
        }

        B cast(Person... p) {
            cast.addAll(Arrays.asList(p));
            return this;
        }

        B crew(Person... p) {
            crew.addAll(Arrays.asList(p));
            return this;
        }

        B seasons(List<Season> s) {
            seasons = s;
            return this;
        }

        B next(int season, int episode) {
            nextSeason = season;
            nextEpisode = episode;
            return this;
        }

        Title build() {
            return new Title(id, kind, name, year, genres, runtime, rating, score, synopsis, art, offers, lists, cast,
                    crew, seasons, nextSeason, nextEpisode);
        }
    }

    // ---- TITLES -------------------------------------------------------------------------------------------------

    private static List<Title> build() {
        List<B> b = new ArrayList<>();

        // ---- Movies in the imported watchlists (16) ----
        b.add(movie("arrival", "Arrival", 2016, 116).genres("Sci-fi", "Drama").rated("PG-13", 7.9)
                .synopsis("Mysterious spacecraft touch down around the globe, and linguist Louise Banks races to understand the visitors before fear pushes the world to war.")
                .art("arrival").offers(sub(PRIME)).offers(stores(3.99, 9.99)).lists(MY)
                .cast(p("Amy Adams", "Louise Banks"), p("Jeremy Renner", "Ian Donnelly"), p("Forest Whitaker", "Col. Weber"), p("Michael Stuhlbarg", "Agent Halpern"), p("Tzi Ma", "General Shang"))
                .crew(p("Denis Villeneuve", "Director")));
        b.add(movie("dune", "Dune: Part Two", 2024, 166).genres("Sci-fi", "Adventure").rated("PG-13", 8.5)
                .synopsis("Paul Atreides unites with Chani and the Fremen on a path of revenge against the conspirators who destroyed his family.")
                .art("dune").offers(sub(MAX)).offers(stores(5.99, 19.99)).lists(MY)
                .cast(p("Timothée Chalamet", "Paul Atreides"), p("Zendaya", "Chani"), p("Rebecca Ferguson", "Lady Jessica"), p("Javier Bardem", "Stilgar"), p("Austin Butler", "Feyd-Rautha"))
                .crew(p("Denis Villeneuve", "Director")));
        b.add(movie("past-lives", "Past Lives", 2023, 106).genres("Drama", "Romance").rated("PG-13", 7.9)
                .synopsis("Two deeply connected childhood friends are wrested apart, then reunite two decades later for one fateful week in New York.")
                .art("past-lives").offers(sub(HULU)).offers(stores()).lists(MY)
                .cast(p("Greta Lee", "Nora"), p("Teo Yoo", "Hae Sung"), p("John Magaro", "Arthur"))
                .crew(p("Celine Song", "Director")));
        b.add(movie("mitchells", "The Mitchells vs. the Machines", 2021, 114).genres("Animation", "Comedy").rated("PG", 7.6)
                .synopsis("A quirky family road trip is upended when the world’s devices rise up, and the Mitchells become humanity’s unlikely last hope.")
                .art("mitchells").offers(sub(NETFLIX)).lists(FRIENDS)
                .cast(p("Abbi Jacobson", "Katie Mitchell"), p("Danny McBride", "Rick Mitchell"), p("Maya Rudolph", "Linda Mitchell"), p("Mike Rianda", "Aaron Mitchell"))
                .crew(p("Mike Rianda", "Director")));
        b.add(movie("coda", "CODA", 2021, 111).genres("Drama").rated("PG-13", 8.0)
                .synopsis("The only hearing member of a deaf family discovers a passion for singing and must choose between her dream and the family fishing business.")
                .art("coda").offers(sub(APPLETVPLUS)).lists(HOUSEHOLD)
                .cast(p("Emilia Jones", "Ruby Rossi"), p("Troy Kotsur", "Frank Rossi"), p("Marlee Matlin", "Jackie Rossi"), p("Daniel Durant", "Leo Rossi"))
                .crew(p("Siân Heder", "Director")));
        b.add(movie("moon", "Moon", 2009, 97).genres("Sci-fi", "Mystery").rated("R", 7.8)
                .synopsis("Near the end of a three-year solo stint mining helium-3 on the far side of the Moon, an astronaut makes a discovery that changes everything.")
                .art("moon").offers(stores(3.99, 9.99)).lists(HOUSEHOLD)
                .cast(p("Sam Rockwell", "Sam Bell"), p("Dominique McElligott", "Tess Bell"))
                .crew(p("Duncan Jones", "Director")));
        b.add(movie("knives-out", "Knives Out", 2019, 130).genres("Mystery", "Comedy").rated("PG-13", 7.9)
                .synopsis("A renowned crime novelist dies just after his 85th birthday, and an inquisitive detective is hired to investigate his eccentric family.")
                .art("knives-out").offers(sub(NETFLIX)).offers(stores()).lists(FRIENDS)
                .cast(p("Daniel Craig", "Benoit Blanc"), p("Ana de Armas", "Marta Cabrera"), p("Chris Evans", "Ransom Drysdale"), p("Jamie Lee Curtis", "Linda Drysdale"))
                .crew(p("Rian Johnson", "Director")));
        b.add(movie("budapest", "The Grand Budapest Hotel", 2014, 100).genres("Comedy").rated("R", 8.1)
                .synopsis("A legendary concierge and his trusted lobby boy are caught up in the theft of a priceless painting and a battle for a family fortune.")
                .art("budapest").offers(sub(DISNEY)).offers(stores(2.99, 9.99)).lists(HOUSEHOLD)
                .cast(p("Ralph Fiennes", "M. Gustave"), p("Tony Revolori", "Zero Moustafa"), p("Saoirse Ronan", "Agatha"), p("Willem Dafoe", "J.G. Jopling"))
                .crew(p("Wes Anderson", "Director")));
        b.add(movie("eeaao", "Everything Everywhere All at Once", 2022, 139).genres("Sci-fi", "Comedy").rated("R", 7.8)
                .synopsis("A laundromat owner audited by the IRS discovers she must connect with parallel-universe versions of herself to save the multiverse.")
                .art("dune").offers(sub(HULU)).offers(stores()).lists(HOUSEHOLD)
                .cast(p("Michelle Yeoh", "Evelyn Wang"), p("Ke Huy Quan", "Waymond Wang"), p("Stephanie Hsu", "Joy Wang"), p("Jamie Lee Curtis", "Deirdre Beaubeirdre"))
                .crew(p("Daniel Kwan", "Director"), p("Daniel Scheinert", "Director")));
        b.add(movie("oppenheimer", "Oppenheimer", 2023, 180).genres("Drama", "History").rated("R", 8.3)
                .synopsis("The story of J. Robert Oppenheimer and the race to build the atomic bomb, and the security hearing that followed.")
                .art("past-lives").offers(sub(PRIME)).offers(stores(5.99, 19.99)).lists(MY)
                .cast(p("Cillian Murphy", "J. Robert Oppenheimer"), p("Emily Blunt", "Kitty Oppenheimer"), p("Robert Downey Jr.", "Lewis Strauss"), p("Matt Damon", "Leslie Groves"))
                .crew(p("Christopher Nolan", "Director")));
        b.add(movie("killers", "Killers of the Flower Moon", 2023, 206).genres("Drama", "Crime").rated("R", 7.6)
                .synopsis("Members of the Osage Nation are murdered under mysterious circumstances in 1920s Oklahoma, sparking a major FBI investigation.")
                .art("arrival").offers(sub(APPLETVPLUS)).lists(HOUSEHOLD)
                .cast(p("Leonardo DiCaprio", "Ernest Burkhart"), p("Lily Gladstone", "Mollie Burkhart"), p("Robert De Niro", "William Hale"))
                .crew(p("Martin Scorsese", "Director")));
        b.add(movie("poor-things", "Poor Things", 2023, 141).genres("Comedy", "Sci-fi").rated("R", 7.8)
                .synopsis("Brought back to life by an unorthodox scientist, Bella Baxter runs off on a whirlwind adventure across the continents.")
                .art("mitchells").offers(sub(HULU)).offers(stores()).lists(FRIENDS)
                .cast(p("Emma Stone", "Bella Baxter"), p("Mark Ruffalo", "Duncan Wedderburn"), p("Willem Dafoe", "Godwin Baxter"))
                .crew(p("Yorgos Lanthimos", "Director")));
        b.add(movie("maestro", "Maestro", 2023, 129).genres("Drama", "Music").rated("R", 6.6)
                .synopsis("A portrait of the lifelong relationship between Leonard Bernstein and Felicia Montealegre Cohn Bernstein.")
                .art("coda").offers(sub(NETFLIX)).lists(MY)
                .cast(p("Bradley Cooper", "Leonard Bernstein"), p("Carey Mulligan", "Felicia Montealegre"))
                .crew(p("Bradley Cooper", "Director")));
        b.add(movie("zone", "The Zone of Interest", 2023, 105).genres("Drama", "History").rated("PG-13", 7.4)
                .synopsis("The commandant of Auschwitz and his wife build a dream life for their family in a house and garden next to the camp.")
                .art("moon").offers(sub(MAX)).offers(stores()).lists(HOUSEHOLD)
                .cast(p("Christian Friedel", "Rudolf Höss"), p("Sandra Hüller", "Hedwig Höss"))
                .crew(p("Jonathan Glazer", "Director")));
        b.add(movie("anatomy", "Anatomy of a Fall", 2023, 151).genres("Drama", "Mystery").rated("R", 7.7)
                .synopsis("A woman is suspected of her husband’s murder, and their partially sighted son faces a moral dilemma as the main witness.")
                .art("knives-out").offers(sub(HULU)).offers(stores()).lists(FRIENDS)
                .cast(p("Sandra Hüller", "Sandra Voyter"), p("Swann Arlaud", "Vincent Renzi"), p("Milo Machado-Graner", "Daniel"))
                .crew(p("Justine Triet", "Director")));
        b.add(movie("american-fiction", "American Fiction", 2023, 117).genres("Comedy", "Drama").rated("R", 7.5)
                .synopsis("A frustrated novelist writes an outlandish book under a pseudonym to prove a point, and it becomes a runaway hit.")
                .art("budapest").offers(sub(PRIME)).offers(stores()).lists(MY)
                .cast(p("Jeffrey Wright", "Monk Ellison"), p("Sterling K. Brown", "Cliff Ellison"), p("Issa Rae", "Sintara Golden"))
                .crew(p("Cord Jefferson", "Director")));

        // ---- Shows in the imported watchlists (12) ----
        b.add(show("severance", "Severance", 2022).genres("Sci-fi", "Thriller").rated("TV-MA", 8.7)
                .synopsis("Office workers at Lumon Industries have their work and personal memories surgically divided, until one team starts to unravel the truth.")
                .art("severance").offers(sub(APPLETVPLUS)).lists(MY)
                .seasons(seasons(52, new String[]{"Good News About Hell", "Half Loop", "In Perpetuity", "The You You Are", "The Grim Barbarity of Optics and Design", "Hide and Seek", "Defiant Jazz", "What’s for Dinner?", "The We We Are"}, 10))
                .next(1, 3)
                .cast(p("Adam Scott", "Mark S."), p("Britt Lower", "Helly R."), p("Zach Cherry", "Dylan G."), p("John Turturro", "Irving B."), p("Patricia Arquette", "Harmony Cobel"))
                .crew(p("Dan Erickson", "Creator")));
        b.add(show("the-bear", "The Bear", 2022).genres("Comedy", "Drama").rated("TV-MA", 8.5)
                .synopsis("A young chef from the fine-dining world comes home to Chicago to run his family’s chaotic sandwich shop.")
                .art("the-bear").offers(sub(HULU)).lists(MY)
                .seasons(seasons(30, new String[]{"System", "Hands", "Brigade", "Dogs", "Sheridan", "Ceres", "Review", "Braciole"}, 10, 10))
                .next(1, 1)
                .cast(p("Jeremy Allen White", "Carmy Berzatto"), p("Ayo Edebiri", "Sydney Adamu"), p("Ebon Moss-Bachrach", "Richie Jerimovich"))
                .crew(p("Christopher Storer", "Creator")));
        b.add(show("dark", "Dark", 2017).genres("Sci-fi", "Mystery").rated("TV-MA", 8.7)
                .synopsis("A missing child sets four families on a frantic hunt for answers that spans three generations in a small German town.")
                .art("dark").offers(sub(NETFLIX)).lists(FRIENDS)
                .seasons(seasons(55, 10, 8, 8)).next(2, 4)
                .cast(p("Louis Hofmann", "Jonas Kahnwald"), p("Lisa Vicari", "Martha Nielsen"), p("Oliver Masucci", "Ulrich Nielsen"))
                .crew(p("Baran bo Odar", "Creator"), p("Jantje Friese", "Creator")));
        b.add(show("last-of-us", "The Last of Us", 2023).genres("Drama", "Sci-fi").rated("TV-MA", 8.7)
                .synopsis("Twenty years after a pandemic, a hardened survivor is hired to smuggle a 14-year-old girl out of an oppressive quarantine zone.")
                .art("last-of-us").offers(sub(MAX)).lists(HOUSEHOLD)
                .seasons(seasons(58, 9, 7)).next(1, 1)
                .cast(p("Pedro Pascal", "Joel Miller"), p("Bella Ramsey", "Ellie Williams"))
                .crew(p("Craig Mazin", "Creator"), p("Neil Druckmann", "Creator")));
        b.add(show("shogun", "Shōgun", 2024).genres("Drama", "History").rated("TV-MA", 8.6)
                .synopsis("In feudal Japan, a shipwrecked English sailor becomes entangled in the struggle of a powerful lord fighting for his life.")
                .art("shogun").offers(sub(HULU)).lists(HOUSEHOLD)
                .seasons(seasons(60, 10)).next(1, 1)
                .cast(p("Hiroyuki Sanada", "Yoshii Toranaga"), p("Cosmo Jarvis", "John Blackthorne"), p("Anna Sawai", "Toda Mariko"))
                .crew(p("Rachel Kondo", "Creator"), p("Justin Marks", "Creator")));
        b.add(show("fallout", "Fallout", 2024).genres("Sci-fi", "Drama").rated("TV-MA", 8.3)
                .synopsis("Two hundred years after the apocalypse, a gentle vault dweller ventures out into the irradiated wasteland of Los Angeles.")
                .art("fallout").offers(sub(PRIME)).lists(MY)
                .seasons(seasons(58, 8)).next(1, 5)
                .cast(p("Ella Purnell", "Lucy MacLean"), p("Walton Goggins", "The Ghoul"), p("Aaron Moten", "Maximus"))
                .crew(p("Graham Wagner", "Creator"), p("Geneva Robertson-Dworet", "Creator")));
        b.add(show("our-planet", "Our Planet", 2019).genres("Documentary").rated("TV-G", 9.3)
                .synopsis("A look at the breathtaking diversity of habitats around the world, and what we stand to lose.")
                .art("our-planet").offers(sub(NETFLIX)).lists(HOUSEHOLD)
                .seasons(seasons(50, 8, 4)).next(1, 2)
                .cast(p("David Attenborough", "Narrator")).crew(p("Alastair Fothergill", "Producer")));
        b.add(show("microcosmos", "Journey to the Microcosmos", 2019).genres("Documentary").rated("TV-G", 8.8)
                .synopsis("Close-up footage of the tiny organisms that live all around us, with gentle narration about how they live.")
                .art("microcosmos").offers(free(YOUTUBE)).lists(FRIENDS)
                .seasons(seasons(12, 12)).next(1, 1)
                .cast(p("Hank Green", "Narrator")).crew(p("James Weiss", "Microscopist")));
        b.add(show("peripheral", "The Peripheral", 2022).genres("Sci-fi", "Thriller").rated("TV-MA", 7.6)
                .synopsis("A gamer in rural America is offered a beta test that turns out to be a window into a dangerous future London.")
                .art("peripheral").offers(sub(PRIME)).lists(FRIENDS)
                .seasons(seasons(60, 8)).next(1, 1)
                .cast(p("Chloë Grace Moretz", "Flynne Fisher"), p("Gary Carr", "Wilf Netherton"), p("Jack Reynor", "Burton Fisher"))
                .crew(p("Scott B. Smith", "Creator")));
        b.add(show("the-crown", "The Crown", 2016).genres("Drama", "History").rated("TV-MA", 8.6)
                .synopsis("The reign of Queen Elizabeth II, and the political rivalries and romances that shaped the second half of the 20th century.")
                .art("shogun").offers(sub(NETFLIX)).lists(HOUSEHOLD)
                .seasons(seasons(58, 10, 10, 10, 10, 10, 10)).next(3, 2)
                .cast(p("Claire Foy", "Queen Elizabeth II"), p("Olivia Colman", "Queen Elizabeth II"), p("Matt Smith", "Prince Philip"))
                .crew(p("Peter Morgan", "Creator")));
        b.add(show("wheel-of-time", "The Wheel of Time", 2021).genres("Fantasy", "Adventure").rated("TV-14", 7.1)
                .synopsis("A powerful sorceress sets out with five young villagers, one of whom may be the prophesied Dragon Reborn.")
                .art("fallout").offers(sub(PRIME)).lists(MY)
                .seasons(seasons(60, 8, 8, 8)).next(2, 1)
                .cast(p("Rosamund Pike", "Moiraine Damodred"), p("Josha Stradowski", "Rand al’Thor"))
                .crew(p("Rafe Judkins", "Creator")));
        b.add(show("blue-planet", "Blue Planet II", 2017).genres("Documentary").rated("TV-G", 9.3)
                .synopsis("A journey through the world’s oceans, from coral reefs to the deep sea, revealing new creatures and behaviour.")
                .art("our-planet").offers(sub(MAX)).lists(MY)
                .seasons(seasons(50, 7)).next(1, 1)
                .cast(p("David Attenborough", "Narrator")).crew(p("James Honeyborne", "Producer")));

        // ---- Catalogue-only titles, so search finds more than the library ----
        b.add(show("carnival-row", "Carnival Row", 2019).genres("Fantasy", "Mystery").rated("TV-MA", 7.7)
                .synopsis("In a Victorian fantasy city, a human detective and a fairy refugee rekindle a dangerous affair amid a string of murders.")
                .art("dark").offers(sub(PRIME)).seasons(seasons(55, 8, 10)).next(1, 1)
                .cast(p("Orlando Bloom", "Rycroft Philostrate"), p("Cara Delevingne", "Vignette Stonemoss")));
        b.add(movie("the-arrival-1996", "The Arrival", 1996, 115).genres("Sci-fi", "Thriller").rated("PG-13", 6.3)
                .synopsis("A radio astronomer picks up a signal from deep space and uncovers a hidden presence on Earth.")
                .art("moon").offers(rent(APPLETV, 2.99, "HD"), buy(APPLETV, 7.99, "HD"))
                .cast(p("Charlie Sheen", "Zane Zaminsky"), p("Lindsay Crouse", "Ilana Green")));
        b.add(movie("blade-runner-2049", "Blade Runner 2049", 2017, 164).genres("Sci-fi", "Drama").rated("R", 8.0)
                .synopsis("A young blade runner’s discovery of a long-buried secret leads him to track down a former blade runner who has been missing for 30 years.")
                .art("dune").offers(stores(3.99, 12.99))
                .cast(p("Ryan Gosling", "K"), p("Harrison Ford", "Rick Deckard"), p("Ana de Armas", "Joi")).crew(p("Denis Villeneuve", "Director")));
        b.add(movie("interstellar", "Interstellar", 2014, 169).genres("Sci-fi", "Adventure").rated("PG-13", 8.7)
                .synopsis("A team of explorers travels through a wormhole in space in an attempt to ensure humanity’s survival.")
                .art("moon").offers(stores(3.99, 9.99))
                .cast(p("Matthew McConaughey", "Cooper"), p("Anne Hathaway", "Brand"), p("Jessica Chastain", "Murph")).crew(p("Christopher Nolan", "Director")));
        b.add(movie("marvel-one-shots", "Marvel One-Shots", 2011, 60).genres("Action").rated("PG-13", 6.9)
                .synopsis("A collection of short films that expand the stories around the Marvel Cinematic Universe.")
                .art("mitchells").offers(sub(DISNEY)));
        b.add(show("larva-island", "Larva Island", 2018).genres("Animation", "Comedy").rated("TV-Y7", 7.2)
                .synopsis("Two larvae shipwrecked on a tropical island get up to slapstick mischief with a stranded human.")
                .art("budapest").offers(sub(NETFLIX)).seasons(seasons(7, 26)).next(1, 1));
        b.add(movie("carnival-of-souls", "Carnival of Souls", 1962, 78).genres("Horror").rated("NR", 7.0)
                .synopsis("After surviving a car accident, a church organist is drawn to an abandoned pavilion by a ghostly figure.")
                .art("knives-out").offers(free(YOUTUBE)));
        b.add(show("mrs-maisel", "The Marvelous Mrs. Maisel", 2017).genres("Comedy", "Drama").rated("TV-MA", 8.7)
                .synopsis("A 1950s housewife discovers she has a knack for stand-up comedy and leaves her life behind to pursue it.")
                .art("past-lives").offers(sub(PRIME)).seasons(seasons(55, 8, 10, 8, 8, 9)).next(1, 1)
                .cast(p("Rachel Brosnahan", "Midge Maisel"), p("Alex Borstein", "Susie Myerson")));
        b.add(show("marvel-assembled", "Marvel Studios: Assembled", 2021).genres("Documentary").rated("TV-PG", 7.4)
                .synopsis("Behind-the-scenes looks at how Marvel Studios’ films and series were made.")
                .art("severance").offers(sub(DISNEY)).seasons(seasons(45, 12)).next(1, 1));
        b.add(show("daredevil", "Marvel’s Daredevil", 2015).genres("Action", "Crime").rated("TV-MA", 8.6)
                .synopsis("A blind lawyer by day fights crime as a masked vigilante in New York’s Hell’s Kitchen by night.")
                .art("the-bear").offers(sub(DISNEY)).seasons(seasons(55, 13, 13, 13)).next(1, 1)
                .cast(p("Charlie Cox", "Matt Murdock"), p("Vincent D’Onofrio", "Wilson Fisk")));
        b.add(movie("harvey-girls", "The Harvey Girls", 1946, 102).genres("Musical", "Western").rated("NR", 6.8)
                .synopsis("A group of young women travel west to work as waitresses at a new restaurant in a rowdy frontier town.")
                .art("coda").offers(rent(APPLETV, 2.99, "HD"), buy(APPLETV, 9.99, "HD"))
                .cast(p("Judy Garland", "Susan Bradley"), p("John Hodiak", "Ned Trent")));
        b.add(show("carnival-eats", "Carnival Eats", 2014).genres("Food").rated("TV-G", 7.0)
                .synopsis("A food lover tours fairs and carnivals across North America in search of the most outrageous treats.")
                .art("dune").offers(sub(HULU)).seasons(seasons(22, 13, 13)).next(1, 1));
        b.add(show("carnivale", "Carnivàle", 2003).genres("Drama", "Fantasy").rated("TV-MA", 8.1)
                .synopsis("During the Dust Bowl, a young fugitive with healing powers joins a travelling carnival caught up in a battle between good and evil.")
                .art("moon").offers(sub(MAX)).seasons(seasons(55, 12, 12)).next(1, 1));
        b.add(show("911", "9-1-1", 2018).genres("Drama", "Action").rated("TV-14", 7.8)
                .synopsis("First responders in Los Angeles balance high-pressure emergencies with their own personal lives.")
                .art("last-of-us").offers(sub(HULU)).seasons(seasons(43, 10, 18, 18)).next(1, 1));
        b.add(show("911-lone-star", "9-1-1: Lone Star", 2020).genres("Drama", "Action").rated("TV-14", 7.4)
                .synopsis("A New York firefighter and his son move to Austin to rebuild a firehouse after a tragedy.")
                .art("fallout").offers(sub(HULU)).seasons(seasons(43, 10, 14)).next(1, 1));

        List<Title> out = new ArrayList<>(b.size());
        for (B t : b) out.add(t.build());
        return out;
    }
}
