<!-- 文件作用：提示已登录用户当前账号缺少访问目标页面的权限。 -->
<template>
  <main class="state-page">
    <strong>403</strong><h1>无权访问</h1><p>当前账号没有访问此页面的权限。</p><RouterLink to="/dashboard">
      返回工作台
    </RouterLink>
  </main>
</template>
<style scoped>.state-page{min-height:70vh;display:grid;place-content:center;justify-items:center}.state-page strong{color:var(--el-color-warning);font-size:72px}.state-page h1{margin:8px}.state-page p{color:#8792a3}</style>
