<template>
  <main class="not-found">
    <p class="code">
      404
    </p>
    <h1>页面不存在</h1>
    <RouterLink to="/">
      返回首页
    </RouterLink>
  </main>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 12px;
}

.code {
  margin: 0;
  color: var(--el-color-primary);
  font-size: 64px;
  font-weight: 700;
}

h1 {
  margin: 0;
}
</style>
