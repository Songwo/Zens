import random
import datetime
import uuid

# 配置
NUM_USERS = 20
NUM_POSTS = 1000
OUTPUT_FILE = "large_test_data.sql"

# 数据池
USER_NAMES = ["张三", "李四", "王五", "CodeMaster", "CampusStar", "JavaBoy", "PythonGirl", "StudyHard", "考研人", "打工人", "Alice", "Bob", "Charlie", "David", "Eva", "Frank", "Grace", "Helen", "Ivy", "Jack"]
AVATARS = ["https://api.dicebear.com/7.x/avataaars/svg?seed=" + str(i) for i in range(20)]

CATEGORIES = [
    ("1", "学习交流"),
    ("2", "校园生活"),
    ("3", "情感树洞"),
    ("4", "求职招聘"),
    ("5", "二手交易"),
    ("6", "失物招领")
]

TITLES_PREFIX = ["震惊！", "求助：", "分享一个", "吐槽一下", "有没有人知道", "关于", "发现一个宝藏", "建议学校", "大四学长", "萌新提问"]
TITLES_CONTENT = ["食堂的饭菜", "图书馆占座", "考研复习资料", "Java这门课", "宿舍矛盾", "失恋了", "找实习", "二手自行车", "校园网", "选修课推荐"]
TITLES_SUFFIX = ["真无语", "太好吃了", "求推荐", "怎么办", "避雷", "真的吗", "进来看", "太难了", "好开心", "求带"]

TAGS_POOL = ["#考研", "#内卷", "#食堂", "#脱单", "#实习", "#Java", "#Python", "#四六级", "#吐槽", "#求助", "#二手", "#运动", "#音乐", "#电影", "#游戏", "#恋爱", "#考试", "#放假", "#毕业", "#工作"]

# 辅助函数
def get_random_time():
    days_offset = random.randint(0, 30)
    seconds_offset = random.randint(0, 86400)
    date = datetime.datetime.now() - datetime.timedelta(days=days_offset, seconds=seconds_offset)
    return date.strftime("%Y-%m-%d %H:%M:%S")

def escape(s):
    return s.replace("'", "''")

sql_statements = []

# 1. 生成用户 SQL (如果用户ID已存在需要忽略，这里使用 REPLACE INTO 或 INSERT IGNORE)
# 为了简单，我们手动指定ID 1001-1020
user_ids = []
sql_statements.append("-- 插入用户数据")
for i in range(NUM_USERS):
    uid = f"100{i+1}"
    user_ids.append(uid)
    username = USER_NAMES[i]
    avatar = AVATARS[i]
    sql = f"INSERT IGNORE INTO sys_user (id, username, nickname, password, avatar, status, create_time) VALUES ('{uid}', 'user{uid}', '{username}', '123456', '{avatar}', 1, '{get_random_time()}');"
    sql_statements.append(sql)

# 2. 生成分类 SQL
sql_statements.append("\n-- 插入分类数据")
for cid, cname in CATEGORIES:
    sql = f"INSERT IGNORE INTO sys_category (id, name, sort, status, create_time) VALUES ('{cid}', '{cname}', {cid}, 1, '{get_random_time()}');"
    sql_statements.append(sql)

# 3. 生成帖子 SQL
sql_statements.append("\n-- 插入帖子数据 (1000条)")
for i in range(NUM_POSTS):
    pid = str(uuid.uuid4()).replace("-", "")
    uid = random.choice(user_ids)
    cid = random.choice(CATEGORIES)[0]
    
    title = f"{random.choice(TITLES_PREFIX)}{random.choice(TITLES_CONTENT)}{random.choice(TITLES_SUFFIX)}"
    content = title * 5 + " " + "测试内容" * 5
    
    # 随机生成标签
    num_tags = random.randint(1, 3)
    post_tags = random.sample(TAGS_POOL, num_tags)
    tags_str = " ".join(post_tags)
    
    # 随机生成数据
    view_count = random.randint(0, 10000)
    like_count = random.randint(0, view_count // 10)
    collect_count = random.randint(0, like_count // 2)
    comment_count = random.randint(0, like_count // 3)
    
    # 热度分计算 (简单模拟)
    heat_score = view_count * 0.1 + like_count * 2 + comment_count * 5
    
    # 情感分
    sentiment_score = random.uniform(0, 1)
    
    create_time = get_random_time()
    
    sql = f"INSERT INTO sys_post (id, user_id, category_id, title, content, tags, is_anonymous, view_count, like_count, collect_count, comment_count, heat_score, sentiment_score, status, audit_status, create_time, update_time) VALUES ('{pid}', '{uid}', '{cid}', '{title}', '{content}', '{tags_str}', {random.randint(0, 1)}, {view_count}, {like_count}, {collect_count}, {comment_count}, {heat_score}, {sentiment_score}, 1, 'APPROVED', '{create_time}', '{create_time}');"
    sql_statements.append(sql)

# 写入文件
with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
    f.write("\n".join(sql_statements))

print(f"生成的 SQL 已保存到 {OUTPUT_FILE}")
