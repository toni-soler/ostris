from pathlib import Path
import hashlib, json, yaml
HERE=Path(__file__).resolve().parent
BACKEND=HERE.parents[1]
FRONTEND=BACKEND.parent/"frontend"
cfg=yaml.safe_load((HERE/"table-config.yml").read_text(encoding="utf-8"))
permissions=[{
 "code": item["code"], "moduleKey": "ostris", "resourceKey": item["resource"],
 "actionKey": item["action"], "labelKey": f"ostris.permissions.{item['code'].lower()}",
 "apiPath": item["apiPath"], "description": item["code"].replace("_", " ").title(),
 "sourceType": "OSTRIS"
} for item in cfg.get("permissions", [])]
outputs={
 BACKEND/"src/main/resources/generated/ostris/permission-catalog.generated.json": json.dumps(permissions,indent=2)+"\n",
 FRONTEND/"src/generated/ostris/manifest.generated.json": json.dumps({"module":"ostris","entities":sorted(cfg.get("entities",{}))},indent=2)+"\n",
 FRONTEND/"src/generated/ostris/crudCatalog.generated.json": "[]\n",
}
for path,content in outputs.items(): path.parent.mkdir(parents=True,exist_ok=True); path.write_text(content,encoding="utf-8",newline="\n")
for locale in ['es', 'en', 'ca', 'eu', 'gl', 'de', 'fr', 'it', 'pt', 'pt-BR', 'jp', 'zh']:
 path=FRONTEND/"src/locales"/f"{locale}.json"; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps({"ostris":{"title":"osTRIS"}},ensure_ascii=False,sort_keys=True,indent=2)+"\n",encoding="utf-8",newline="\n")
for area in (BACKEND/"src/main/java/es/idynamicsax/ostris/generated",BACKEND/"src/main/java/es/idynamicsax/ostris/custom"): area.mkdir(parents=True,exist_ok=True); (area/".gitkeep").touch()
files={str(p.relative_to(BACKEND.parent)).replace("\\","/"):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(outputs)}
(BACKEND/".generated-manifest.json").write_text(json.dumps({"generatorVersion":1,"metadata":"scripts/gen-idax-ostris/table-config.yml","files":files},indent=2,sort_keys=True)+"\n",encoding="utf-8",newline="\n")
