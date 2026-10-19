export interface Chapter { id:number;title:string;date:string;location:string;role:'SOLDIER'|'TANK';character:string;history:string;mission:string;source:string;sourceTitle:string;theme:string }
export interface CampaignState {
  chapter:Chapter
  units:{ id:string;name:string;x:number;y:number;hp:number;ally:boolean;tank:boolean;angle:number }[]
  walls:{x:number;y:number;width:number;height:number}[]
  areas:{x:number;y:number;width:number;height:number;kind:string;label:string;layer:number}[]
  objectives:{label:string;x:number;y:number;layer:number;kind:string}[]
  objective:number;layer:number;ammo:number;reload:number;ticksLeft:number
  shots:{x:number;y:number;tx:number;ty:number;life:number;blast:boolean}[]
  status:string;winner:string|null;notice:string;revision:number
}
