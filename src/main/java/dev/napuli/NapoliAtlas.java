package dev.napuli;

import java.util.*;
import net.minecraft.core.BlockPos;

/** North is -Z, east is +X. A schematic game atlas, not a cadastral or 1:1 street survey. */
public final class NapoliAtlas {
    public record Bounds(int minX,int minZ,int maxX,int maxZ) {
        public long area(){return (long)(maxX-minX)*(maxZ-minZ);}
        public boolean contains(int x,int z){return x>=minX&&x<maxX&&z>=minZ&&z<maxZ;}
    }
    // Encloses the authored v31 town, its sports facilities and the existing bay scenery.
    public static final Bounds ORIGINAL=new Bounds(-544,-272,480,272);
    public static final Bounds EXPANDED=new Bounds(-2048,-816,2048,544);
    public enum Kind { SQUARE, PARK, PALACE, CHURCH, CLOISTER, CASTLE, MUSEUM, CATACOMBS, STATION, TOWERS, STADIUM, EXHIBITION, SCIENCE, PIER, RUINS, SEASIDE }
    public record Place(String id,String name,String district,Kind kind,int x,int z,int radius,boolean original) {
        public int approachZ(){if(kind==Kind.PIER)return z-radius+4;int az=z+radius+4;return !original&&NapoliExpansion.water(x,az)?z-radius-4:az;}
        public BlockPos arrival(){if(id.equals("mergellina"))return new BlockPos(-285,64,52);if(id.equals("castel_ovo"))return new BlockPos(0,64,68);if(id.equals("castel_nuovo"))return NapoliMunicipio.CASTLE.west(2);if(id.equals("municipio"))return new BlockPos(215,64,3);if(id.equals("teatro_san_carlo"))return NapoliTheatreQuarter.FORECOURT;if(id.equals("galleria_umberto"))return NapoliDistrict.GALLERIA.south(NapoliDistrict.ARM+4);int az=approachZ();return new BlockPos(x,kind==Kind.PIER?66:NapoliExpansion.height(x,az)+1,az);}
        public boolean near(int px,int pz,int padding){return Math.abs(px-x)<=radius+padding&&Math.abs(pz-z)<=radius+padding;}
    }
    private static Place p(String id,String name,String district,Kind kind,int x,int z,int radius){return new Place(id,name,district,kind,x,z,radius,false);}
    public static final List<Place> PLACES=List.of(
        new Place("plebiscito","Piazza del Plebiscito","San Ferdinando",Kind.SQUARE,0,26,20,true),
        new Place("teatro_san_carlo","Teatro San Carlo","San Ferdinando",Kind.PALACE,99,-102,15,true),
        new Place("galleria_umberto","Galleria Umberto I","San Ferdinando",Kind.SQUARE,NapoliDistrict.GX,NapoliDistrict.GZ,NapoliDistrict.ARM,true),
        new Place("dante","Piazza Dante","Avvocata",Kind.SQUARE,0,-181,22,true),
        p("duomo","Duomo di Napoli","San Lorenzo",Kind.CHURCH,375,-310,30),
        new Place("municipio","Piazza Municipio","Porto",Kind.SQUARE,215,3,20,true),
        new Place("castel_nuovo","Castel Nuovo · Maschio Angioino","Porto",Kind.CASTLE,243,35,19,true),
        new Place("castel_ovo","Castel dell’Ovo","San Ferdinando",Kind.CASTLE,0,98,20,true),
        new Place("mergellina","Mergellina · nucleo esistente","Chiaia",Kind.SEASIDE,-285,49,15,true),
        p("vanvitelli","Piazza Vanvitelli","Vomero",Kind.SQUARE,-790,-300,26),
        p("santelmo","Castel Sant’Elmo","Vomero",Kind.CASTLE,-655,-315,38),
        p("san_martino","Certosa di San Martino","Vomero",Kind.CLOISTER,-625,-210,30),
        p("floridiana","Villa Floridiana","Vomero",Kind.PARK,-850,-165,48),
        p("artisti","Piazza degli Artisti","Arenella",Kind.SQUARE,-865,-425,25),
        p("medaglie","Piazza Medaglie d’Oro","Arenella",Kind.SQUARE,-850,-545,27),
        p("amedeo","Piazza Amedeo","Chiaia",Kind.SQUARE,-650,-50,23),
        p("martiri","Piazza dei Martiri","Chiaia",Kind.SQUARE,-585,65,26),
        p("vittoria","Piazza Vittoria","Chiaia",Kind.SQUARE,-670,155,23),
        p("villa_comunale","Villa Comunale","Chiaia",Kind.PARK,-790,145,48),
        p("sannazaro","Piazza Sannazaro","Chiaia",Kind.SQUARE,-970,150,27),
        p("donnanna","Palazzo Donn’Anna","Posillipo",Kind.PALACE,-1160,245,32),
        p("marechiaro","Marechiaro · Calata Ponticello","Posillipo",Kind.SEASIDE,-1400,385,30),
        p("virgiliano","Parco Virgiliano","Posillipo",Kind.PARK,-1550,260,57),
        p("pausilypon","Parco archeologico del Pausilypon","Posillipo",Kind.RUINS,-1670,405,45),
        p("nisida","Nisida · belvedere esterno","Bagnoli",Kind.SEASIDE,-1870,365,39),
        p("scienza","Città della Scienza","Bagnoli",Kind.SCIENCE,-1795,170,44),
        p("pontile","Pontile nord di Bagnoli","Bagnoli",Kind.PIER,-1920,70,45),
        p("mostra","Mostra d’Oltremare","Fuorigrotta",Kind.EXHIBITION,-1400,-50,49),
        p("maradona","Stadio Diego Armando Maradona","Fuorigrotta",Kind.STADIUM,-1450,-240,76),
        p("tecchio","Piazzale Vincenzo Tecchio","Fuorigrotta",Kind.SQUARE,-1260,-125,27),
        p("soccavo","Soccavo · Piazza Giovanni XXIII","Soccavo",Kind.SQUARE,-1280,-445,26),
        p("pianura","Pianura · Piazza San Giorgio","Pianura",Kind.SQUARE,-1830,-485,27),
        p("scampia","Parco Ciro Esposito","Scampia",Kind.PARK,-650,-680,42),
        p("secondigliano","Corso Secondigliano","Secondigliano",Kind.SQUARE,200,-690,26),
        p("mann","Museo Archeologico Nazionale · MANN","Stella",Kind.MUSEUM,-20,-310,32),
        p("cavour","Piazza Cavour","Stella",Kind.SQUARE,130,-320,29),
        p("sanita","Basilica di Santa Maria della Sanità","Stella",Kind.CHURCH,-125,-410,30),
        p("fontanelle","Cimitero delle Fontanelle","Stella",Kind.CATACOMBS,-330,-465,32),
        p("san_gennaro","Catacombe di San Gennaro","San Carlo all’Arena",Kind.CATACOMBS,-140,-545,27),
        p("capodimonte","Museo e Real Bosco di Capodimonte","San Carlo all’Arena",Kind.PALACE,-10,-650,55),
        p("orto","Orto Botanico di Napoli","San Carlo all’Arena",Kind.PARK,350,-450,42),
        p("carlo_iii","Piazza Carlo III","San Lorenzo",Kind.SQUARE,510,-470,31),
        p("albergo","Real Albergo dei Poveri","San Lorenzo",Kind.PALACE,510,-590,52),
        p("capuana","Porta Capuana","San Lorenzo",Kind.CASTLE,575,-335,27),
        p("garibaldi","Piazza Garibaldi e Napoli Centrale","Vicaria",Kind.STATION,680,-195,49),
        p("nazionale","Piazza Nazionale","Vicaria",Kind.SQUARE,790,-385,30),
        p("direzionale","Centro Direzionale","Poggioreale",Kind.TOWERS,1040,-310,63),
        p("mercato","Piazza Mercato","Mercato",Kind.SQUARE,550,25,29),
        p("carmine","Piazza del Carmine e Basilica","Mercato",Kind.CHURCH,650,85,30),
        p("ponticelli","Piazza Vincenzo Aprea","Ponticelli",Kind.SQUARE,1600,-150,31),
        p("barra","Piazza De Franchis","Barra",Kind.SQUARE,1820,70,30),
        p("san_giovanni","Piazza Giambattista Pacichelli","San Giovanni a Teduccio",Kind.SQUARE,1390,225,29),
        p("pietrarsa","Museo Nazionale Ferroviario di Pietrarsa","San Giovanni a Teduccio",Kind.MUSEUM,1780,355,49)
    );
    public record Road(String name,String from,String to){}
    public static final List<Road> ROADS=List.of(
        new Road("Via Toledo","dante","plebiscito"),
        new Road("Via Partenope","plebiscito","castel_ovo"),
        new Road("Via Caracciolo","castel_ovo","mergellina"),
        new Road("Piazza Sannazaro / Mergellina","mergellina","sannazaro"),
        new Road("Via Salvator Rosa / Arenella","dante","medaglie"),
        new Road("Via Scarlatti e Via Luca Giordano","vanvitelli","artisti"),
        new Road("Via Bernini","artisti","medaglie"),
        new Road("Via Morghen / San Martino","vanvitelli","santelmo"),
        new Road("Largo San Martino","santelmo","san_martino"),
        new Road("Via Cimarosa","vanvitelli","floridiana"),
        new Road("Corso Vittorio Emanuele","san_martino","amedeo"),
        new Road("Via dei Mille","amedeo","martiri"),
        new Road("Via Calabritto","martiri","vittoria"),
        new Road("Riviera di Chiaia","vittoria","villa_comunale"),
        new Road("Riviera di Chiaia / Mergellina","villa_comunale","sannazaro"),
        new Road("Via Posillipo","sannazaro","donnanna"),
        new Road("Via Posillipo / Marechiaro","donnanna","marechiaro"),
        new Road("Via Tito Lucrezio Caro","marechiaro","virgiliano"),
        new Road("Discesa Coroglio","virgiliano","pausilypon"),
        new Road("Via Coroglio","pausilypon","scienza"),
        new Road("Via Nisida · belvedere","scienza","nisida"),
        new Road("Via Coroglio / Bagnoli","scienza","pontile"),
        new Road("Via Diocleziano","pontile","mostra"),
        new Road("Viale Kennedy","mostra","tecchio"),
        new Road("Via Giulio Cesare","tecchio","sannazaro"),
        new Road("Via Claudio","mostra","maradona"),
        new Road("Via Cinthia","maradona","soccavo"),
        new Road("Via Montagna Spaccata","soccavo","pianura"),
        new Road("Collegamento collinare","medaglie","scampia"),
        new Road("Collegamento nord","scampia","secondigliano"),
        new Road("Via Pessina","dante","mann"),
        new Road("Via Foria","mann","cavour"),
        new Road("Via Duomo","cavour","duomo"),
        new Road("Via dei Tribunali","duomo","capuana"),
        new Road("Via Santa Teresa / Via Sanità","mann","sanita"),
        new Road("Via Fontanelle","sanita","fontanelle"),
        new Road("Corso Amedeo di Savoia","sanita","san_gennaro"),
        new Road("Via Capodimonte","san_gennaro","capodimonte"),
        new Road("Via Miano","capodimonte","secondigliano"),
        new Road("Via Foria","cavour","orto"),
        new Road("Via Foria","orto","carlo_iii"),
        new Road("Piazza Carlo III","carlo_iii","albergo"),
        new Road("Via Cesare Rosaroll","carlo_iii","capuana"),
        new Road("Corso Garibaldi","capuana","garibaldi"),
        new Road("Corso Novara","garibaldi","nazionale"),
        new Road("Corso Meridionale","garibaldi","direzionale"),
        new Road("Corso Garibaldi","garibaldi","carmine"),
        new Road("Piazza del Carmine","carmine","mercato"),
        new Road("Corso Umberto I","municipio","garibaldi"),
        new Road("Via Argine","direzionale","ponticelli"),
        new Road("Corso Sirena","ponticelli","barra"),
        new Road("Corso San Giovanni a Teduccio","carmine","san_giovanni"),
        new Road("Corso San Giovanni / Pietrarsa","san_giovanni","pietrarsa"),
        new Road("Via delle Repubbliche Marinare","san_giovanni","barra")
    );
    private static final Map<String,Place> INDEX=PLACES.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Place::id,p->p));
    public static Place find(String id){return INDEX.get(id);}
    public static Place nearest(int x,int z){return PLACES.stream().min(Comparator.comparingDouble(p->Math.hypot(x-p.x(),z-p.z()))).orElseThrow();}
    public static double distance(Road road,int x,int z){var a=find(road.from());var b=find(road.to());double dx=b.x()-a.x(),dz=b.z()-a.z();double t=Math.clamp(((x-a.x())*dx+(z-a.z())*dz)/(dx*dx+dz*dz),0,1);return Math.hypot(x-a.x()-t*dx,z-a.z()-t*dz);}
    private NapoliAtlas(){}
}
