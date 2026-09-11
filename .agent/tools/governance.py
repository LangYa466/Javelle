#!/usr/bin/env python3
"""Dependency-free P02 governance validation and atomic ledger update."""
import argparse, fcntl, hashlib, json, os, pathlib, re, shutil, stat, subprocess, sys, tempfile, time, tomllib, zipfile

ROOT=pathlib.Path(__file__).resolve().parents[2]
STATES={'TODO','READY','RUNNING','IMPLEMENTED','VERIFIED','ACCEPTED','BLOCKED','FAILED','REOPENED'}

def load(p): return json.loads(pathlib.Path(p).read_text(encoding='utf-8'))
def fail(msg): raise ValueError(msg)
def norm(p):
 q=pathlib.PurePosixPath(p)
 if q.is_absolute() or '..' in q.parts or str(q) in ('','.'): fail('unsafe ownership path: '+p)
 return str(q)
def overlap(a,b): return a==b or a.startswith(b.rstrip('/')+'/') or b.startswith(a.rstrip('/')+'/')

def validate_tasks(d):
 if d.get('schemaVersion')!=1 or not isinstance(d.get('tasks'),list): fail('tasks schema')
 ids=[x.get('id') for x in d['tasks']]
 if None in ids or len(ids)!=len(set(ids)): fail('duplicate/missing task id')
 known=set(ids)
 for x in d['tasks']:
  if x.get('status') not in STATES: fail('invalid status '+x.get('id','?'))
  deps=x.get('dependsOn'); ev=x.get('evidence')
  if not isinstance(deps,list) or not isinstance(ev,list): fail('missing dependency/evidence arrays '+x['id'])
  if any(y not in known for y in deps): fail('unknown dependency '+x['id'])
  if x['status'] in {'RUNNING','IMPLEMENTED','VERIFIED','ACCEPTED'} and not x.get('owner'): fail('active task missing owner '+x['id'])
  if x['status'] in {'VERIFIED','ACCEPTED'} and not ev: fail('verified task missing evidence '+x['id'])
 graph={x['id']:x['dependsOn'] for x in d['tasks']}; seen=set(); active=set()
 def visit(n):
  if n in active: fail('task cycle at '+n)
  if n not in seen:
   active.add(n)
   for z in graph[n]: visit(z)
   active.remove(n); seen.add(n)
 for n in graph: visit(n)

def validate_ownership(d,stale_seconds=86400):
 if d.get('schemaVersion')!=1 or not isinstance(d.get('agents'),list): fail('ownership schema')
 locks=[]; now=time.time()
 for a in d['agents']:
  if not a.get('id') or not isinstance(a.get('ownedPaths'),list): fail('owner shape')
  for p in a['ownedPaths']:
   p=norm(p)
   if a.get('status') in {'RUNNING','running','active'}:
    for oid,op in locks:
     if oid!=a['id'] and overlap(p,op): fail(f'overlapping writers: {a["id"]}:{p} vs {oid}:{op}')
    locks.append((a['id'],p))
    if a.get('acquiredEpoch') and now-float(a['acquiredEpoch'])>stale_seconds: fail('stale active lock '+a['id'])

def expected_requirements(plan):
 t=pathlib.Path(plan).read_text(encoding='utf-8')
 ids=set(re.findall(r'\*\*(P\d\d-\d\d)\*\*',t)+re.findall(r'\*\*(UAT-\d\d)\*\*',t))
 ids.update(re.findall(r'^## ([BC]\d)\.',t,re.M)); return ids
def validate_requirements(d,plan):
 if d.get('schemaVersion')!=1 or not isinstance(d.get('requirements'),list): fail('requirements schema')
 rows=d['requirements']; ids=[x.get('id') for x in rows]
 if None in ids or len(ids)!=len(set(ids)): fail('duplicate/missing requirement id')
 exp=expected_requirements(plan); got=set(ids)
 if exp!=got: fail('requirements coverage missing='+str(sorted(exp-got))+' extra='+str(sorted(got-exp)))
 for x in rows:
  if x.get('implementationStatus') not in {'NOT_IMPLEMENTED','IMPLEMENTED'} or x.get('verificationStatus') not in {'NOT_VERIFIED','VERIFIED'}: fail('bad requirement status '+x['id'])
  if not x.get('ownerRole') or not x.get('section'): fail('missing mapping '+x['id'])

def validate_all(root):
 validate_tasks(load(root/'.agent/TASKS.json')); validate_ownership(load(root/'.agent/OWNERSHIP.json'))
 validate_requirements(load(root/'compatibility/requirements.json'),root/'prompts/JAVELLE_IMPLEMENTATION_PLAN.md')
 c=tomllib.loads((root/'.codex/config.toml').read_text()); a=c.get('agents',{})
 if a!={'enabled':True,'max_concurrent_threads_per_session':4}: fail('unsafe/unknown codex agent config')
 role=tomllib.loads((root/'.codex/agents/javelle-reviewer.toml').read_text())
 if set(role)!={'name','description','developer_instructions'} or any(k in role for k in ('model','sandbox','approval_policy')): fail('unsafe reviewer role config')
 pol=load(root/'.agent/schema/worktree-policy.json')
 if pol.get('sharedWorktree',{}).get('allowedGitMutators')!=['integration']: fail('integration-only commit policy missing')

def atomic_update(path,input_path,expected_sha):
 path=path.resolve(); path.parent.mkdir(parents=True,exist_ok=True); lock=path.with_name('.'+path.name+'.lock')
 with lock.open('a+b') as lf:
  fcntl.flock(lf,fcntl.LOCK_EX)
  marker=os.environ.get('JAVELLE_ATOMIC_LOCK_MARKER')
  if marker: pathlib.Path(marker).write_text('locked\n',encoding='utf-8')
  hold=float(os.environ.get('JAVELLE_ATOMIC_HOLD_SECONDS','0'))
  if hold: time.sleep(hold)
  old=path.read_bytes(); actual=hashlib.sha256(old).hexdigest()
  if actual!=expected_sha: fail('fingerprint collision')
  data=pathlib.Path(input_path).read_bytes(); json.loads(data)
  fd,tmp=tempfile.mkstemp(prefix='.'+path.name+'.',dir=path.parent)
  try:
   with os.fdopen(fd,'wb') as f: f.write(data); f.flush(); os.fsync(f.fileno())
   if os.environ.get('JAVELLE_ATOMIC_FAIL_AFTER_FILE_FSYNC')=='1': raise OSError('injected post-fsync failure')
   os.replace(tmp,path)
   dfd=os.open(path.parent,os.O_RDONLY); os.fsync(dfd); os.close(dfd)
  except BaseException:
   try: os.unlink(tmp)
   except FileNotFoundError: pass
   raise

def line_endings(root):
 if (root/'gradlew').read_bytes().find(b'\r\n')>=0: fail('gradlew must be LF')
 bat=(root/'gradlew.bat').read_bytes()
 if b'\r\n' not in bat: fail('gradlew.bat must preserve CRLF')
 if not os.stat(root/'gradlew').st_mode & stat.S_IXUSR: fail('gradlew not executable')

def archive_check(root):
 files=[root/'LICENSE',root/'LICENSE-CLASSPATH-EXCEPTION-2.0',root/'THIRD-PARTY-NOTICES.md']
 def make(p):
  with zipfile.ZipFile(p,'w') as z:
   for f in sorted(files):
    i=zipfile.ZipInfo(f.name,(1980,1,1,0,0,0)); i.external_attr=0o100644<<16
    z.writestr(i,f.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
 with tempfile.TemporaryDirectory() as d:
  a=pathlib.Path(d)/'a.zip'; b=pathlib.Path(d)/'b.zip'; make(a); make(b)
  if a.read_bytes()!=b.read_bytes(): fail('archive bytes differ')

def product_archive_check(root):
 def ignored(_path,names):
  return {n for n in names if n in {'.git','.gradle','build','node_modules','logs','tmp'} or n=='vcs.xml'}
 tmp_root=root/'.agent/tmp'; tmp_root.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory(dir=tmp_root) as d:
  outputs=[]
  for run in ('a','b'):
   work=pathlib.Path(d)/run
   shutil.copytree(root,work,ignore=ignored)
   env=os.environ.copy(); env['GRADLE_USER_HOME']=str(root/'.agent/tmp/P02-gradle-home')
   result=subprocess.run([str(work/'gradlew'),'--no-daemon','--dependency-verification=strict','clean','jar'],cwd=work,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=300)
   if result.returncode: fail('product archive build '+run+' failed: '+result.stdout[-2000:])
   jars={str(p.relative_to(work)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(work.glob('*/build/libs/*.jar'))}
   if not jars: fail('product archive build produced no jars')
   outputs.append(jars)
  if outputs[0]!=outputs[1]: fail('product archive bytes differ: '+str(outputs))
  print('PRODUCT_ARCHIVES_REPRODUCIBLE count='+str(len(outputs[0])))

def selftest(root):
 (root/'.agent/tmp').mkdir(parents=True,exist_ok=True)
 validate_all(root); line_endings(root); archive_check(root)
 good=load(root/'.agent/TASKS.json')
 cases=[]
 x=json.loads(json.dumps(good)); x['tasks'].append(dict(x['tasks'][0])); cases.append(x)
 x=json.loads(json.dumps(good)); x['tasks'][0]['dependsOn']=['MISSING']; cases.append(x)
 for x in cases:
  try: validate_tasks(x); fail('negative unexpectedly passed')
  except ValueError: pass
 own={'schemaVersion':1,'limits':{},'agents':[{'id':'a','status':'RUNNING','ownedPaths':['build.gradle.kts']},{'id':'b','status':'RUNNING','ownedPaths':['build.gradle.kts/x']}]}
 try: validate_ownership(own); fail('collision unexpectedly passed')
 except ValueError: pass
 stale={'schemaVersion':1,'limits':{},'agents':[{'id':'old','status':'RUNNING','ownedPaths':['x'],'acquiredEpoch':1}]}
 try: validate_ownership(stale); fail('stale lock unexpectedly passed')
 except ValueError: pass
 with tempfile.TemporaryDirectory(dir=root/'.agent/tmp') as d:
  p=pathlib.Path(d)/'ledger.json'; p.write_text('{"v":1}\n'); n=pathlib.Path(d)/'new.json'; n.write_text('{"v":2}\n'); before=p.read_bytes()
  try: atomic_update(p,n,'0'*64); fail('collision update passed')
  except ValueError: pass
  if p.read_bytes()!=before: fail('failed update changed prior state')
  expected=hashlib.sha256(before).hexdigest(); os.environ['JAVELLE_ATOMIC_FAIL_AFTER_FILE_FSYNC']='1'
  try: atomic_update(p,n,expected); fail('injected fsync failure passed')
  except OSError: pass
  finally: os.environ.pop('JAVELLE_ATOMIC_FAIL_AFTER_FILE_FSYNC',None)
  if p.read_bytes()!=before: fail('post-fsync failure changed prior state')
  marker=pathlib.Path(d)/'locked'; env=os.environ.copy(); env['JAVELLE_ATOMIC_LOCK_MARKER']=str(marker); env['JAVELLE_ATOMIC_HOLD_SECONDS']='0.25'
  cmd=[sys.executable,str(pathlib.Path(__file__).resolve()),'atomic-update',str(p),str(n),'--expected-sha256',expected]
  first=subprocess.Popen(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,env=env)
  for _ in range(100):
   if marker.exists(): break
   time.sleep(0.01)
  if not marker.exists(): first.kill(); fail('concurrent lock marker missing')
  second=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
  first_result=first.wait(timeout=5)
  if sorted((first_result,second.returncode))!=[0,1]: fail('concurrent updates did not serialize')
  if load(p)!={'v':2}: fail('concurrent update corrupted ledger')

def main():
 p=argparse.ArgumentParser(); s=p.add_subparsers(dest='cmd',required=True)
 s.add_parser('validate'); s.add_parser('selftest'); s.add_parser('line-endings'); s.add_parser('archive-repro'); s.add_parser('product-archives')
 a=s.add_parser('atomic-update'); a.add_argument('path'); a.add_argument('input'); a.add_argument('--expected-sha256',required=True)
 x=p.parse_args()
 if x.cmd=='validate': validate_all(ROOT)
 elif x.cmd=='selftest': selftest(ROOT)
 elif x.cmd=='line-endings': line_endings(ROOT)
 elif x.cmd=='archive-repro': archive_check(ROOT)
 elif x.cmd=='product-archives': product_archive_check(ROOT)
 else: atomic_update(pathlib.Path(x.path),x.input,x.expected_sha256)
 print('GOVERNANCE_OK',x.cmd)
if __name__=='__main__': main()
