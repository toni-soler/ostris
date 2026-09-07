from pathlib import Path
import hashlib, json, yaml
HERE=Path(__file__).resolve().parent
BACKEND=HERE.parents[1]
FRONTEND=BACKEND.parent/"frontend"
cfg=yaml.safe_load((HERE/"table-config.yml").read_text(encoding="utf-8"))
workspace_candidates=(BACKEND.parent/".idax-module.yml",BACKEND.parent/"ostris"/".idax-module.yml")
manifest_path=next((path for path in workspace_candidates if path.is_file()),None)
if manifest_path is None: raise FileNotFoundError("Cannot locate .idax-module.yml")
manifest=yaml.safe_load(manifest_path.read_text(encoding="utf-8"))
permission_fields=("code","resourceKey","actionKey","fieldKey","labelKey","apiPath","description")
permission_catalog={"schemaVersion":1,"moduleKey":"ostris","sourceType":"IDAX_MODULE","permissions":[{field:item.get(field) for field in permission_fields} for item in sorted(manifest.get("permissions",[]),key=lambda value:value["code"])]}
outputs={
 BACKEND/"src/main/resources/generated/ostris/permission-catalog.generated.json": json.dumps(permission_catalog,ensure_ascii=False,sort_keys=True,indent=2)+"\n",
 FRONTEND/"src/generated/ostris/manifest.generated.json": json.dumps({"module":"ostris","entities":sorted(cfg.get("entities",{}))},indent=2)+"\n",
 FRONTEND/"src/generated/ostris/crudCatalog.generated.json": "[]\n",
}
for path,content in outputs.items(): path.parent.mkdir(parents=True,exist_ok=True); path.write_text(content,encoding="utf-8",newline="\n")
for locale in ['es', 'en', 'ca', 'eu', 'gl', 'de', 'fr', 'it', 'pt', 'pt-BR', 'jp', 'zh']:
 path=FRONTEND/"src/locales"/f"{locale}.json"; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps({"ostris":{"title":"osTRIS"}},ensure_ascii=False,sort_keys=True,indent=2)+"\n",encoding="utf-8",newline="\n")
for area in (BACKEND/"src/main/java/es/idynamicsax/ostris/generated",BACKEND/"src/main/java/es/idynamicsax/ostris/custom"): area.mkdir(parents=True,exist_ok=True); (area/".gitkeep").touch()
files={str(p.relative_to(BACKEND.parent)).replace("\\","/"):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(outputs)}
(BACKEND/".generated-manifest.json").write_text(json.dumps({"generatorVersion":1,"metadata":"scripts/gen-idax-ostris/table-config.yml","files":files},indent=2,sort_keys=True)+"\n",encoding="utf-8",newline="\n")
