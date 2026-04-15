const { defineConfig } = require('cypress');

module.exports = defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:8081',
    supportFile: 'cypress/support/e2e.js',
    specPattern: 'cypress/e2e/**/*.cy.js',
    /** Video toàn bộ spec — lưu tại cypress/videos/ (xem lại luồng; báo cáo Word dùng ảnh trong bao-cao/). */
    video: true,
    /** Ảnh khi test fail (mặc định). Ảnh khi pass do hook trong support/e2e.js. */
    screenshotOnRunFailure: true,
    screenshotsFolder: 'cypress/screenshots',
    videosFolder: 'cypress/videos',
    defaultCommandTimeout: 10000,
    pageLoadTimeout: 30000,
  },
});
