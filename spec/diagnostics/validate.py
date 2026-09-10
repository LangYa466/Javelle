#!/usr/bin/env python3
import json,pathlib,re,sys
root=pathlib.Path(__file__).resolve().parents[2]
cat=json.loads((root/'spec/diagnostics/catalog.json').read_text())
fix=json.loads((root/'spec/diagnostics/fixtures.json').read_text())
grammar=json.loads((root/'spec/grammar/grammar-contract.json').read_text())
codes=[x['code'] for x in cat['diagnostics']]; cases=[x['id'] for x in fix['cases']]
assert len(codes)==len(set(codes)) and all(re.fullmatch(r'JV-[A-Z]+-\d{4}',x) for x in codes)
assert len(cases)==len(set(cases)) and {'positive','negative','behavior','recovery'}<=set(x['kind'] for x in fix['cases'])
known=set(codes)
for x in fix['cases']:
 assert set(x.get('expect',[]))<=known, x['id']
assert grammar['forbiddenTokens']==['SEMICOLON']
assert grammar['jlsChapterCoverage']==list(range(1,20))
assert grammar['implementationStatus']=='NOT_IMPLEMENTED' and fix['verificationStatus']=='NOT_VERIFIED'
ebnf=(root/grammar['formalGrammar']).read_text()
for production in ('basicForHeader','enhancedForHeader','resourceSpecification','enumBody','propertyAccessorBlock'):
 assert re.search(r'^'+production+r'\s*=',ebnf,re.M), production
# Closed production-reference audit. Strip comments, quoted terminals and prose predicates.
formal=re.sub(r'\(\*[\s\S]*?\*\)',' ',ebnf)
formal=re.sub(r'\?[\s\S]*?\?',' ',formal)
formal=re.sub(r'"(?:[^"\\]|\\.)*"',' ',formal)
defined=set(re.findall(r'^([a-z][A-Za-z0-9_]*)\s*=',formal,re.M))
referenced=set(re.findall(r'\b(?:JAVA_[A-Za-z0-9_]+|[a-z][A-Za-z0-9_]*)\b',formal))
keywords={'position'}
imports=set(grammar['javaImports']['productions'])
undefined=referenced-defined-imports-keywords
assert not undefined, 'undefined productions: '+str(sorted(undefined))
used_imports={x for x in referenced if x.startswith('JAVA_')}
assert used_imports<=imports and imports-used_imports==set(), 'Java import drift'
assert '[ "=", JAVA_variableInitializer ]' in ebnf
required={'SYN-FOR-TERNARY','SYN-RETURN-NEWLINE','SYN-RESOURCE-MULTILINE-INIT','SYN-ENUM-ANON-BODY','PROP-POSTFIX-ORDER','PROP-COMPOUND-NARROW','PROP-COMPUTED-NO-INIT','FIELD-EXPLICIT-NO-INIT'}
assert required<=set(cases), required-set(cases)
print(f'SPEC_MANIFEST_OK diagnostics={len(codes)} fixtures={len(cases)} undefinedRefs={len(undefined)} javaImports={len(imports)}')
