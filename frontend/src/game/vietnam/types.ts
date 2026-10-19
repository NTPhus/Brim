import type { TacticalVehicleView } from '@/types/tactical'
export interface Chapter { id:number;title:string;date:string;location:string;role:'SOLDIER'|'TANK';character:string;history:string;mission:string;source:string;sourceTitle:string;theme:string }
export interface CampaignState {
  chapter:Chapter
  units:{ id:string;name:string;x:number;y:number;hp:number;ally:boolean;tank:boolean;angle:number;hullAngle:number;maxHealth:number;tacticalView:TacticalVehicleView|null }[]
  walls:{x:number;y:number;width:number;height:number}[]
  areas:{x:number;y:number;width:number;height:number;kind:string;label:string;layer:number}[]
  objectives:{label:string;x:number;y:number;layer:number;kind:string}[]
  reserveAmmo:number;lastCommandSeq:number;rulesVersion:string
  supplies:{id:string;x:number;y:number;layer:number;kind:string;quantity:number}[]
  projectiles:{id:number;x:number;y:number;angle:number}[]
  objective:number;layer:number;ammo:number;reload:number;ticksLeft:number
  shots:{x:number;y:number;tx:number;ty:number;life:number;blast:boolean}[]
  status:string;winner:string|null;notice:string;revision:number
}
