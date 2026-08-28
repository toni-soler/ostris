import fs from 'node:fs';
const locales=['es','en','ca','eu','gl','de','fr','it','pt','pt-BR','jp','zh'];
for(const locale of locales){const value=JSON.parse(fs.readFileSync(new URL(`../src/locales/${locale}.json`,import.meta.url)));if(value?.ostris?.title!=='osTRIS')throw new Error(`invalid locale ${locale}`);}
const manifest=JSON.parse(fs.readFileSync(new URL('../src/generated/ostris/manifest.generated.json',import.meta.url)));if(manifest.module!=='ostris')throw new Error('invalid generated manifest');
console.log('osTRIS frontend generated artifacts and 12 locales: OK');
