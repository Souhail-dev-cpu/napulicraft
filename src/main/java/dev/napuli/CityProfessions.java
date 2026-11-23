package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Shared workplaces and AI tools, rather than a separate AI for each profession. */
final class CityProfessions {
    enum Category {
        FOOD(Items.BREAD), TRADE(Items.PAPER), SERVICES(Items.BRUSH), PUBLIC(Items.BOOK),
        TRANSPORT(Items.COMPASS), MEDIA(Items.SPYGLASS), BUILDING(Items.IRON_PICKAXE),
        OFFICE(Items.WRITABLE_BOOK), HEALTH(Items.GLASS_BOTTLE), SECURITY(Items.IRON_SWORD);
        final Item tool;
        Category(Item tool) { this.tool=tool; }
        BlockPos site() { return new BlockPos(-530+ordinal()%5*35,64,-35+ordinal()/5*30); }
    }
    static boolean night(NapoliCivicLife.Job job) {
        return job==NapoliCivicLife.Job.NIGHT_DOCTOR||job==NapoliCivicLife.Job.NIGHT_DRIVER
                ||job==NapoliCivicLife.Job.NIGHT_NURSE||job==NapoliCivicLife.Job.CARABINIERE;
    }
    static int start(NapoliCivicLife.Job job) { return night(job)?12500:2500; }
    static int end(NapoliCivicLife.Job job) { return night(job)?22500:10500; }
    static boolean open(NapoliCivicLife.Job job,long time,long day) {
        return time>=start(job)&&time<end(job)&&(night(job)||Math.floorMod(day,7)<5);
    }
    static String rank(NapoliCivicLife.Job job,int level) {
        if(job==NapoliCivicLife.Job.CARABINIERE) return new String[]{"Recluta","Carabiniere","Appuntato","Sergente","Maresciallo","Tenente","Capitano","Comandante"}[Math.clamp(level,0,7)];
        return new String[]{"Apprendista","Addetto","Esperto","Responsabile"}[Math.clamp(level,0,3)];
    }
    static int wage(NapoliCivicLife.Job job,int rank) { return job.wage+rank*4; }
    static boolean eligible(NapoliCivicLife.Job job,NapoliCivicLife.Citizen citizen,CitySimulation.Life life,long day) {
        return citizen.age(day)>=(job.category==Category.HEALTH?21:18)
                &&(job.category!=Category.SECURITY||life.reputation()>=40)&&life.health()>=45;
    }
    static void build(net.minecraft.server.level.ServerLevel city) {
        for(var category:Category.values()) {
            var c=category.site();
            for(var p:BlockPos.betweenClosed(c.offset(-2,-1,-2),c.offset(20,-1,14)))
                city.setBlock(p,net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),2);
            for(var p:BlockPos.betweenClosed(c,c.offset(18,5,12))) {
                boolean wall=p.getX()==c.getX()||p.getX()==c.getX()+18||p.getZ()==c.getZ()||p.getZ()==c.getZ()+12;
                var block=p.getY()==69?net.minecraft.world.level.block.Blocks.BRICK_SLAB
                        :wall&&!(p.getZ()==c.getZ()+12&&p.getX()>=c.getX()+7&&p.getX()<=c.getX()+9&&p.getY()<67)
                        ?net.minecraft.world.level.block.Blocks.CALCITE:net.minecraft.world.level.block.Blocks.AIR;
                city.setBlock(p,block.defaultBlockState(),2);
            }
            city.setBlock(c.offset(3,0,3),net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(),2);
            city.setBlock(c.offset(3,3,5),net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState(),2);
            NapoliEmploymentMap.sign(city,c.offset(6,0,13),name(category),"Lavori e servizi");
        }
    }
    static String name(Category c) { return switch(c) {
        case FOOD -> "Ristorazione";case TRADE -> "Mercato e negozi";case SERVICES -> "Servizi di quartiere";
        case PUBLIC -> "Scuola e municipio";case TRANSPORT -> "Stazione e trasporti";case MEDIA -> "Studio e cultura";
        case BUILDING -> "Artigiani e cantiere";case OFFICE -> "Uffici e imprese";case HEALTH -> "Ospedale e farmacia";
        case SECURITY -> "Caserma e soccorso";
    }; }
    private CityProfessions() {}
}
