package com.brim.game.game;

import com.brim.game.game.tactical.*;
import com.brim.game.game.tank.TankGame.Wall;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TacticalMechanicsTest {
    private final TacticalBalance b=TacticalBalance.standard();
    @Test void heldTriggerCannotSkipReloadOrCreateAmmunition() {
        var weapon=new CannonState(b);int shots=0;
        for(int i=0;i<400;i++){if(weapon.fire())shots++;weapon.advance(.05);}
        assertEquals(4,shots);assertEquals(4,weapon.total());
        for(int i=0;i<4;i++){weapon.advance(6);assertTrue(weapon.fire());}
        assertEquals(CannonState.Phase.EMPTY,weapon.phase());assertEquals(0,weapon.total());
        weapon.beginLoading();weapon.advance(100);assertFalse(weapon.fire());
        assertEquals(12,weapon.refill(99));assertEquals(12,weapon.total());
        assertEquals(1,weapon.view().chamber());assertEquals(11,weapon.view().reserve());assertEquals(0,weapon.refill(99));
        weapon.advance(6);assertTrue(weapon.fire());assertEquals(11,weapon.total());
    }
    private double travel(double dt){
        var v=new HeavyVehicle(b,123);double distance=0;
        for(int i=0;i<Math.round(20/dt);i++){var m=v.advance(dt,1,0,0,0,0);distance+=m.distance();v.traveled(m.distance());}
        assertEquals(b.fuelRange()-distance,v.view().fuel(),1e-6);assertEquals(45,v.view().speed(),1e-6);
        return distance;
    }
    @Test void accelerationAndFuelDependOnElapsedTimeAndDistanceRatherThanPackets(){
        assertEquals(849.375,travel(.05),1e-6);assertEquals(travel(.05),travel(.1),1e-6);
        var v=new HeavyVehicle(b,1);for(int i=0;i<200;i++){v.advance(.05,1,0,0,0,0);v.blocked();}
        assertEquals(b.fuelRange(),v.view().fuel());
        v.traveled(3000);assertEquals(0,v.advance(.1,1,0,0,0,0).distance());
        assertEquals(100,v.refuel(100));assertTrue(v.advance(.1,1,0,0,0,0).distance()>0);
    }
    @Test void turretAndHullHaveIndependentBoundedTurnRates(){
        var v=new HeavyVehicle(b,1);double hull=0,turret=0;
        for(int i=0;i<20;i++){var m=v.advance(.05,0,1,hull,turret,Math.PI);hull=m.hullAngle();turret=m.turretAngle();}
        assertEquals(35,Math.toDegrees(hull),1e-6);assertEquals(50,Math.toDegrees(turret),1e-6);
    }
    @Test void repairsReserveOneKitCancelOnDamageAndCannotExceedFieldRepairCap(){
        var v=new HeavyVehicle(b,1);assertFalse(v.startRepair(1000,1200));assertTrue(v.startRepair(800,1200));
        assertEquals(0,v.view().repairKits());assertEquals(800,v.advanceRepair(3,false,800,1200));
        assertEquals(800,v.advanceRepair(.1,true,800,1200));assertEquals(1,v.view().repairKits());
        assertTrue(v.startRepair(800,1200));for(int i=0;i<79;i++)assertEquals(800,v.advanceRepair(.1,false,800,1200));
        assertEquals(840,v.advanceRepair(.1,false,800,1200));assertEquals(0,v.view().repairKits());
        assertFalse(v.startRepair(200,1200));
    }
    @Test void armorUsesHullDirectionAndSplashDoesNotCrossOpaqueCover(){
        assertEquals(.55,TacticalDamage.armor(0,10,0,0,0));assertEquals(1,TacticalDamage.armor(0,-10,0,0,0));
        assertEquals(.8,TacticalDamage.armor(0,0,10,0,0));assertEquals(1,TacticalDamage.armor(0,0,0,0,0));
        assertEquals(110,TacticalDamage.radial(60,120,220));assertEquals(0,TacticalDamage.radial(120,120,220));
        var wall=new Wall(150,50,10,100);
        assertTrue(TacticalGeometry.clear(List.of(wall),145,100,100,100,BattleObstacle::blocksBlast));
        assertFalse(TacticalGeometry.clear(List.of(wall),145,100,200,100,BattleObstacle::blocksBlast));
        wall.setBlocksBlast(false);assertTrue(TacticalGeometry.clear(List.of(wall),145,100,200,100,BattleObstacle::blocksBlast));
        assertFalse(TacticalGeometry.clear(List.of(wall),145,100,200,100,BattleObstacle::blocksVision));
    }
    @Test void repairReservationCountsTowardCapacityAndNeverReducesHealth(){
        var v = new HeavyVehicle(b, 1);
        assertTrue(v.startRepair(500, 1200));
        assertEquals(1, v.addRepairKits(10));
        v.interruptRepair();
        assertEquals(2, v.view().repairKits());
        assertEquals(0, v.addRepairKits(10));
        assertTrue(v.startRepair(500, 1200));
        assertEquals(1000, v.advanceRepair(8, false, 1000, 1200));
    }
    @Test void dispersionIsDeterministicAndSettlingImprovesAccuracy(){
        var a=new HeavyVehicle(b,42);var same=new HeavyVehicle(b,42);
        for(int i=0;i<20;i++)assertEquals(a.shotAngle(0),same.shotAngle(0));
        for(int i=0;i<30;i++)a.advance(.05,0,0,0,0,0);
        assertTrue(a.view().settled());for(int i=0;i<50;i++)assertTrue(Math.abs(Math.toDegrees(a.shotAngle(0)))<=.6);
    }
}
