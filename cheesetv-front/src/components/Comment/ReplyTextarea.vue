<template>
  <div class="comment-textarea">
    <div class="box-normal">
      <div class="reply-box-avatar">
        <div class="cheesetv-avatar">
          <VAvatar :img="this.user.avatar" :size="isWideWindow ? 48 : 40" />
        </div>
      </div>
      <div class="reply-box-wrap">
        <div class="textarea-wrap" :class="{ active: isFocus }" v-if="isLogin">
          <textarea
            class="reply-box-textarea"
            :placeholder="placeholder"
            @focus.stop="isFocus = true"
            @blur="handleBlur"
            ref="textarea"
            v-model="content"
            maxlength="2000"
            :style="{
              height: content === '' ? '32px' : '64px',
              lineHeight: content === '' ? '32px' : '1.25',
            }"
          />
        </div>
        <div class="disable-mask" v-else>
          <span
            >请先<span class="to-login-btn" @click="updateDialogVisible(true)"
              >登录</span
            >后发表评论 (・ω・)
          </span>
        </div>
      </div>
    </div>
    <div class="box-expand" v-if="isLogin && isFocus">
      <div
        class="reply-box-send"
        :class="{ 'send-not-allowed': !this.content.trim() }"
      >
        <div class="send-text" @click="handleSendComment">发布</div>
      </div>
    </div>
  </div>
</template>

<script>
import { ElMessage } from "element-plus";
import VAvatar from "../avatar/VAvatar.vue";
import { useUserStore } from "@/store/user";
import { mapState, mapActions } from "pinia";
import { fetchSendComment } from "@/api/video";
export default {
  name: "ReplyTextarea",
  components: {
    VAvatar,
  },
  props: {
    placeholder: {
      type: String,
      default: "你猜我的评论区在等待谁？",
    },
    // 是否是宽屏
    isWideWindow: {
      type: Boolean,
      default() {
        return false;
      },
    },
    commentInfo: {
      type: Object,
      default() {
        return {
          content: "",
          userId: "",
          videoId: "",
          parentCommentId: "",
          rootCommentId: "",
        };
      },
    },
    // 添加一条评论
    addOneComment:{
      type: Function,
      default() {
        return () => {};
      }
    },
  },
  mounted() {},
  data() {
    return {
      content: "", //评论内容
      isFocus: false, //是否聚焦
    };
  },
  computed: {
    ...mapState(useUserStore, ["user", "isLogin"]),
  },
  methods: {
    ...mapActions(useUserStore, ["updateDialogVisible"]),
    // 点击空白处隐藏发布栏
    cancelFocus() {
      // 但是输入框不为空的时候，点击空白不隐藏
      if (this.content.trim()) {
        return;
      }
      this.isFocus = false;
    },
    handleBlur() {
      //   this.cursorStart = this.$refs.textarea.selectionStart;
      //   this.cursorEnd = this.$refs.textarea.selectionEnd;
    },
    async handleSendComment() {
      if (!this.content.trim()) {
        ElMessage.info("你还没输入内容!");
        return;
      }
      try {
        let commentParams = {
          userId: this.user.userId,
          videoId: this.$route.params.videoId,
          content: this.content,
        };

        if (this.commentInfo.parentCommentId) {
          // 回复子评论或者后代评论
          commentParams.parentCommentId = this.commentInfo.parentCommentId;
        }
        // 没有parentCommentId，就是发根评论
        const res = await fetchSendComment(commentParams);
        if (res.code === 200) {
          this.$emit("add-comment", res.data);
          ElMessage.success("评论成功");
          this.addOneComment();
          this.content = "";
          this.isFocus = false;
          this.$refs.textarea.focus();
        }
      } catch (error) {
        ElMessage.error("评论失败:" + error);
      }
    },
    focusTextarea() {
      this.isFocus = true;
      this.$refs.textarea.focus();
    },
  },
};
</script>

<style scoped>
.box-normal {
  display: flex;
  z-index: 2;
}

.reply-box-avatar {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 80px;
  height: 48px;
}

.cheesetv-avatar {
  display: block;
  position: relative;
  background-size: cover;
  border-radius: 50%;
  margin: 0;
  padding: 0;
  image-rendering: -webkit-optimize-contrast;
}

.reply-box-wrap {
  position: relative;
  flex: 1;
  transition: 0.2s;
  border: 1px solid #e3e5e7;
  border-radius: 6px;
  background-color: var(--bg3);
  overflow-x: hidden;
}

.textarea-wrap {
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  width: 100%;
  border-radius: 6px;
  cursor: text;
  overflow: hidden;
  transition: background-color 0.5s ease;
}

.textarea-wrap.active,
.textarea-wrap:hover {
  background-color: var(--bg1);
}

.disable-mask {
  display: flex;
  justify-content: center;
  align-items: center;
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  border-radius: 6px;
  font-size: 12px;
  color: var(--text3);
  background-color: var(--bg3);
}

.to-login-btn {
  padding: 4px 9px;
  border-radius: 4px;
  background-color: var(--main-color);
  color: #fff;
  margin: 0 3px;
  cursor: pointer;
}
.to-login-btn:hover {
  background-color: var(--v_brand_blue_hover);
}

.reply-box-textarea {
  padding: 0 8px;
  width: 100%;
  height: 32px;
  border: none;
  border-radius: 6px;
  background-color: transparent;
  font-family: inherit;
  font-size: 14px;
  line-height: 32px;
  color: #18191c;
  resize: none;
  outline: none;
}
.reply-box-textarea::placeholder {
  color: var(--v_text3);
}
.reply-box-textarea::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.reply-box-textarea::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background-color: #ccc;
}

.box-expand {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-left: 80px;
  margin-top: 8px;
  z-index: 1;
  height: 32px;
  transition: all 0.2s ease-in-out;
}

.box-expand.hide {
  margin-top: 0;
  height: 0;
  overflow: hidden;
  transition: all 0.2s ease-in-out;
}

.reply-box-send {
  float: right;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  width: 70px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  background-color: var(--main-color);
}
.reply-box-send.send-not-allowed {
  cursor: not-allowed;
  background-color: rgba(0, 174, 236, 0.5);
}
.send-text {
  position: absolute;
  z-index: 1;
  font-size: 16px;
  color: var(--text_white);
}
</style>
