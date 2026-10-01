"""Windows 隔离 Fabric 测试；输入由参数指定，不访问玩家数据，不覆盖完整世界生成或 GPU。"""
from pathlib import Path
import subprocess,shutil,json,zipfile,os,re,hashlib,argparse
p=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tag',required=True,help='Fresh run name / 新测试名称')
parser.add_argument('--game',action='append',required=True,metavar='LABEL=ROOT',help='Repeat for each game: ROOT/libs and ROOT/data (or ROOT/game/data)')
parser.add_argument('--acbric-dir',type=Path,default=p.parent/'Acbric')
parser.add_argument('--java-home',type=Path,default=os.environ.get('JAVA_HOME'))
parser.add_argument('--arc-jar',type=Path,help='Default: the only ARC-Overhaul-*.jar in build/libs')
parser.add_argument('--output-root',type=Path,default=p/'build/runtime-tests')
args=parser.parse_args()
if os.name!='nt':parser.error('This runtime harness currently supports Windows only / 当前脚本仅支持 Windows')
if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]*',args.tag):parser.error('Invalid tag; use letters, digits, underscores or hyphens')
if args.java_home is None:parser.error('Set JAVA_HOME or --java-home to JDK 21 / 请指定 JDK 21')
framework=args.acbric_dir.resolve();jdk=args.java_home.resolve()/'bin'
candidates=sorted((p/'build/libs').glob('ARC-Overhaul-*.jar'))
if args.arc_jar is None and len(candidates)!=1:parser.error('Build first; use --arc-jar if multiple versions exist')
arc=(args.arc_jar or candidates[0]).resolve();api=framework/'build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar'
for path in [arc,api,framework/'build/libs/Acbric-1.0-SNAPSHOT.jar',jdk/'java.exe',jdk/'javac.exe']:
    if not path.is_file():parser.error(f'Missing input / 缺少输入: {path}')
loader=framework/'build/dist/Acbric/loader-libs'
if not loader.is_dir() or not list(loader.glob('sponge-mixin-*.jar')):parser.error(f'Build Acbric dist first / 请先构建框架 dist: {loader}')
e=args.output_root.resolve()/args.tag
if e.exists():parser.error(f'Output exists; use a fresh tag / 请使用新名称: {e}')
# 所有输入预检查通过后才建立输出目录；不向游戏输入树写入。
games=[];labels=set()
for spec in args.game:
    label,separator,location=spec.partition('=')
    if not separator or not location or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]*',label):parser.error('--game requires LABEL=ROOT')
    if label.lower() in labels:parser.error(f'Duplicate label: {label}')
    labels.add(label.lower());base=Path(location).resolve()
    data=base/'data' if (base/'data').is_dir() else base/'game/data'
    for name in ['asplit-A.zip','asplit-B.zip']:
        if not (base/'libs'/name).is_file():parser.error(f'Missing game input: {base/"libs"/name}')
    for name in ['fontmetrics','lang']:
        if not (data/name).is_dir():parser.error(f'Missing game resource: {data/name}')
    if e.is_relative_to(base) or base.is_relative_to(e):parser.error('Test output must be separate from game inputs / 输出须与游戏输入分开')
    games.append((label,base,data))
e.mkdir(parents=True)
results=[]
for game,base,data in games:
    run=e/game
    if run.exists():raise RuntimeError(f'Fixture exists: {run}')
    for d in ['libs','loader-libs','game/mods','game/data','userdata','home','appdata','classes']:(run/d).mkdir(parents=True,exist_ok=True)
    for f in (base/'libs').iterdir():
        if f.is_file() and f.suffix.lower() in ['.jar','.zip'] and not re.search('probe|acbric|fabric-loader|sponge-mixin|asm-',f.name,re.IGNORECASE):shutil.copy2(f,run/'libs'/f.name)
    for f in (framework/'build/dist/Acbric/loader-libs').iterdir():
        if f.is_file():shutil.copy2(f,run/'loader-libs'/f.name)
    shutil.copy2(framework/'build/libs/Acbric-1.0-SNAPSHOT.jar',run/'loader-libs/Acbric-1.0-SNAPSHOT.jar')
    shutil.copy2(api,run/'game/mods/acbric-api.jar');shutil.copy2(arc,run/'game/mods/arc.jar')
    for name in ['fontmetrics','lang']:shutil.copytree(data/name,run/'game/data'/name)
    cp=';'.join(str(run/n) for n in ['libs/*','loader-libs/*','libs/asplit-A.zip','libs/asplit-B.zip','game/mods/*'])
    subprocess.run([str(jdk/'javac.exe'),'-J-Dfile.encoding=UTF-8','-J-Dstdout.encoding=UTF-8','-J-Dstderr.encoding=UTF-8','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(run/'classes'),str(p/'tests/ArcRuntimeProbe.java'),str(p/'tests/fixtures/AssetFixtures.java')],check=True)
    with zipfile.ZipFile(run/'libs/arc-probe.jar','w',zipfile.ZIP_DEFLATED) as z:
        for f in (run/'classes').rglob('*.class'):
            if 'fixtures' not in f.parts:z.write(f,f.relative_to(run/'classes').as_posix())
    with zipfile.ZipFile(run/'game/mods/arc-assets-fixture.jar','w',zipfile.ZIP_DEFLATED) as z:
        for f in (run/'classes/regression/fixtures').rglob('*.class'):z.write(f,f.relative_to(run/'classes').as_posix())
        z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'arc_test_assets','version':'1','mixins':['fixtures.mixins.json']}))
        z.writestr('fixtures.mixins.json',json.dumps({'required':True,'package':'regression.fixtures','compatibilityLevel':'JAVA_21','mixins':['AssetFixtures$Arms','AssetFixtures$Background','AssetFixtures$Land'],'injectors':{'defaultRequire':1}}))
    (run/'game/Airships.json').write_text(json.dumps({'mainClass':'regression.ArcRuntimeProbe','classPath':['asplit-A.zip','asplit-B.zip']}),encoding='utf-8')
    (run/'game/launch_settings.json').write_text(json.dumps({'customDataDirectoryLocation':str(run/'userdata')}),encoding='utf-8')
    cmd=[str(jdk/'java.exe'),'-Dsteam=false','-Ddev=true','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',f'-Duser.home={run/"home"}','--add-opens=java.base/java.util=ALL-UNNAMED','-cp',f'{run/"libs"}/*;{run/"loader-libs"}/*','net.fabricmc.loader.impl.launch.knot.KnotClient']
    with (run/'runtime.log').open('w',encoding='utf-8') as out:r=subprocess.run(cmd,cwd=run/'game',env=dict(os.environ,APPDATA=str(run/'appdata')),stdout=out,stderr=subprocess.STDOUT,timeout=60)
    log=(run/'runtime.log').read_text(encoding='utf-8');m=re.search(r'ARC RUNTIME PASS: (\d+) checks',log)
    if r.returncode!=0 or m is None:raise RuntimeError(f'Failed: {run/"runtime.log"}\n{log[-7500:]}')
    results.append({'game':game,'checks':int(m.group(1)),'status':'PASS','gameSha256':{name:hashlib.sha256((base/'libs'/name).read_bytes()).hexdigest() for name in ['asplit-A.zip','asplit-B.zip']}});print(results[-1],flush=True)
summary={'runs':results,'arcSha256':hashlib.sha256(arc.read_bytes()).hexdigest(),'apiSha256':hashlib.sha256(api.read_bytes()).hexdigest(),'limitations':'No full world generation, graphical UI, assets allocation or multiplayer lobby session. Arms/background/land asset methods replaced only in test fixture; native placement, types, incomes and IDs exercised. Native territory tracing uses synthetic separated ownership cells with IDs from real placements, not full territory influence generation. The AI fleet catalogue is registered synthetically (no ConstructionStrategy data dir in the fixture); the injected call site is verified by parsing the shipped WorldMap$2 bytecode, and the native empire-creation stage itself is not executed. The explicit-order(T) checks drive the injected handlers on the real kernel classes and assert their call sites in the shipped bytecode, but never start a live Combat: whether guns really fire at a named defenceless target, and whether carrier aircraft really fly at it, stays a manual check.'}
(e/'summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(f'Results / 测试结果: {e/"summary.json"}')
