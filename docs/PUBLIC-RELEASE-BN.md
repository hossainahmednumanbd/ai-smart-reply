# AI Smart Reply: সবার ব্যবহারের জন্য প্রকাশের প্রস্তুতি

আপডেট: ২৬ অক্টোবর ২০২৬। এটি প্রকাশের প্রস্তুতি, Google Play বা Meta অনুমোদনের নিশ্চয়তা নয়। 0.3.0 এখনো বাস্তব ফোনে সম্পূর্ণ যাচাই প্রয়োজন। Facebook Page কমেন্ট সংযোগ এই সংস্করণে চালু নেই।

## নতুন সুবিধা ব্যবহার

- টেমপ্লেট → নতুন নিয়ম: `দাম|price|মূল্য` লিখে নিজের উত্তর দিন। `|` মানে বিকল্প। পুরো মেসেজ মিলতে চাইলে সুইচ চালু করুন। ইংরেজি keyword অন্য শব্দের মাঝখানে মেলে না।
- প্রথম চালু নিয়ম আগে মেলে। অগ্রাধিকার বাড়াতে ↑ চাপুন। পরীক্ষা বাটনে কোন উত্তর মিলবে দেখুন। সর্বোচ্চ ৩০টি নিয়ম।
- AI অটোতে নিয়ম মিললে নিজের লেখা উত্তর যায়, AI-তে সেই মেসেজ যায় না। না মিললে AI তৈরি করে। টেমপ্লেট মোডে নিয়ম না মিললে সাধারণ টেমপ্লেট কেবল তার নিজস্ব সুইচ চালু থাকলে যায়। 0.3.1 থেকে এই সুইচ ডিফল্টভাবে বন্ধ; আগের সংরক্ষিত উত্তর থাকে।
- হোম/সেটিংস → AI নির্দেশনা ও ব্যবসার তথ্য: ভাষা, যাচাইকৃত ব্যবসার তথ্য, প্রশ্ন-উত্তরের উদাহরণ লিখুন। সংরক্ষণ করুন। এরপরের ম্যানুয়াল ও অটো AI অনুরোধে এগুলো ব্যবহার হয়। এটি prompt customization, Gemini model-এর স্থায়ী training নয়। তথ্য না থাকলে উপস্থিতি/মজুত/দাম/ডেলিভারি নিশ্চিত না করতে বলা হয়েছে। ভুল হওয়ার সম্ভাবনা পুরোপুরি দূর হয়নি।
- সেটিংস → ভুল / আপত্তিকর AI উত্তর রিপোর্ট: সর্বশেষ AI উত্তর ও কারণ পর্যালোচনার জন্য পাঠায়। মূল incoming message পাঠায় না।
- নতুন সংস্করণ 0.3.0 একই স্থায়ী key দিয়ে সাইন করা; 0.2.1-এর ওপর update হবে। আনইনস্টল করবেন না।

## সাধারণ প্রকাশের আগে অবশ্যই সমাধান করতে হবে

1. **Gemini সেবার শর্ত ও billing**: Google-এর বর্তমান শর্তে API ১৮+ এবং professional/business use-এর জন্য। Unpaid service-এ personal/sensitive/confidential content পাঠানো নিষেধ; input/output উন্নয়নের জন্য ব্যবহার ও মানব পর্যালোচনা হতে পারে। ব্যক্তিগত মেসেজ প্রক্রিয়াকরণের জন্য উপযুক্ত paid-service data terms যাচাই ও billing, খরচের সীমা/সতর্কতা নির্ধারণ করুন। এই কাজ আপনার হয়ে paid billing চালু করেনি। প্রয়োজন হলে ব্যবসার ব্যবহারে সীমিত রাখুন; শিশু বা সাধারণ ব্যক্তিগত consumer assistant হিসেবে প্রকাশ করবেন না।
2. **Google Auth Platform**: সঠিক project → Audience → External, Testing থেকে In production / Publish app। Branding-এ নাম, support email, public homepage/privacy link দিন। Google যেসব verification চায় পূরণ করুন। শুধুমাত্র email/profile/OpenID-এর বর্তমান scope রাখুন। Backend Web client ID বদলাবেন না। API key বা signing bundle কাউকে দেবেন না।
3. **স্বাক্ষর**: Play App Signing-এ AAB দিলে Google Play-এর app signing certificate SHA-1 স্থায়ী local APK key থেকে আলাদা হতে পারে। Play Console-এ দেখানো app signing SHA-1 দিয়ে একই package `bd.app.aismartreply`-এর জন্য আরেকটি Android OAuth client যোগ করুন। পুরোনো client রাখুন। আপডেটের key ও upload key backup নিরাপদে রাখুন।
4. **প্রকাশ্য URL যাচাই**: Worker deployment সফল হলে `/`, `/privacy`, `/delete-account` লগইন ছাড়াই অন্য ফোন থেকে খুলে পরীক্ষা করুন। Browser/bot blocking থাকলে আগে স্বাভাবিক human access ঠিক করুন। Google branding verification-এর জন্য নিজের verified domain প্রয়োজন হতে পারে; workers.dev domain ownership verification নিজে থেকে নিশ্চিত হয় না।
5. **বাস্তব support**: hossainahmednumanbd@gmail.com-এ deletion/support request নিয়মিত দেখবেন। যিনি data owner তা যাচাই করে ব্যবস্থা নেবেন। অ্যাপের বাইরে deletion request বাস্তবে নিষ্পত্তি না করলে শুধু policy page থাকলেই চলবে না।
6. **AI রিপোর্ট review**: Cloudflare D1 Console-এ `feedback` table নিয়মিত পর্যালোচনা করে factuality/safety সমস্যায় নির্দেশনা ও moderation উন্নত করুন। Message text public issue/লগে দেবেন না। প্রতিদিন cron পুরোনো report ও আগের দিনের usage মুছে দেয়। Report সর্বোচ্চ প্রায় ৩১ দিন; database backup/provider retention আলাদা। Cron deployment ও execution পরীক্ষা করুন।
7. **খরচ ও অপব্যবহার**: বর্তমান server-এ Google user-প্রতি ২০ AI request/UTC দিন। এটি মোট ব্যবহারকারীর aggregate billing cap নয়। বেশিসংখ্যক account হলে মোট খরচ বাড়বে। public launch-এর আগে Cloudflare/Gemini quota ও budget, abuse monitoring এবং প্রয়োজনীয় total limit নির্ধারণ করুন।

## Google Play জমা দেওয়ার আগে

- বিল্ড target/compile API 36, স্থায়ী signed APK ও AAB তৈরি করে। SDK36-এ ফোনে app update, Google login, AI/template rules, screen lock/background, Stop, permission deny/revoke, network failure, account deletion, reporting, large font/landscape পরীক্ষা করুন। নতুন API target-এর কারণে display ও system behavior আলাদা হতে পারে।
- Play Console developer পরিচয়, যোগাযোগ, store listing, content rating, app access/reviewer instructions, privacy policy, Data safety সঠিকভাবে পূরণ করুন। বর্তমান feature ও data flow-এর সঙ্গে মেলান; “কোনো data সংগ্রহ হয় না” লিখবেন না। Google subject ID, user messages sent for AI, owner profile, deliberate feedback output এবং provider processing বিবেচনা করুন।
- Notification Access কেন দরকার, foreground/background কোন data পড়বে, Cloudflare/Gemini-তে কী যাবে, automatic sending কীভাবে বন্ধ হবে তা permission-এর আগে app দেখায়। Reviewer-কে এই flow দেখান।
- Foreground service type `specialUse` আলাদা review-এর বিষয়। Play Console-এ purpose, interruption impact ও video দিন। ব্যবহারকারী নিজে চালু, ongoing notification, Stop control এবং core need দেখান। Approval নিশ্চিত নয়; rejected হলে feedback অনুযায়ী design বদলাতে হবে। অন্য service type লিখে review এড়াবেন না।
- AI content policy অনুযায়ী harmful output prevention ও in-app report বাস্তবে কাজ করতে হবে; report সংগ্রহের পরে developer-কে review করতে হবে। বর্তমান prompt ও Gemini filters ছাড়াও production usage অনুযায়ী safeguards মূল্যায়ন করুন।
- account/data deletion app ও public web path-এ আছে। Today's quota abuse prevention-এর জন্য daily cleanup পর্যন্ত থাকে, policy-তে স্পষ্ট করা আছে। Sign out এবং account data deletion এক জিনিস নয়।
- সাধারণ SMS, contacts, Accessibility Service, location, camera, microphone, full-screen intent permission বর্তমান app ব্যবহার করে না। Play listing-এ সমর্থিত notification-based behavior সঠিকভাবে বলুন।
- Meta/WhatsApp/Messenger-এর নিজস্ব terms-ও প্রযোজ্য। Android Reply action কাজ করলেই Meta-এর সব policy compliance নিশ্চিত হয় না। Facebook/WhatsApp-এর official app হিসেবে দাবি করবেন না, তাদের logo-কে app identity বানাবেন না।
- নতুন personal developer account-এর ক্ষেত্রে Console নির্ধারিত closed test পূরণ করে production access চাইতে হয়। ন্যূনতম test users/duration আপনার Console ও বর্তমান official help অনুযায়ী অনুসরণ করুন।

## Facebook Page কমেন্টে AI reply: পরবর্তী সংযোগ

Notification listener দিয়ে Page comment management চালু করা হয়নি। নিজের পরিচালিত Page-এর জন্য official Meta Pages API প্রয়োজন।

1. Meta Developer account ও উপযুক্ত Meta app তৈরি; Business verification/App Review প্রয়োজন হলে সম্পন্ন করা।
2. Page বেছে login/connect flow; প্রয়োজনমতো `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_engagement`, `pages_manage_metadata`-এর বর্তমান permission ও access requirement যাচাই।
3. Server-side encrypted Page access token, scoped owner/Page mapping, token expiry/revocation handling। Android APK বা chat-এ secret রাখবেন না।
4. HTTPS webhook verification ও `X-Hub-Signature-256` যাচাই; Page `feed` event subscription। Page নিজের দেওয়া comment/duplicate event বাদ, idempotent job processing, rate limits/retry, disable switch।
5. প্রথমে AI draft + manual approval দিয়ে test। Page owner আলাদা করে auto-comment enable করলে পরে server থেকে reply। Personal-profile comments scraping বা password/browser automation দিয়ে official permissions এড়ানো হবে না।
6. নিজের app roles-এর বাইরে ব্যবহারকারীর Page নিয়ে কাজ করার আগে Meta-র প্রয়োজনীয় Advanced Access/App Review সম্পন্ন করতে হবে। এই account configuration ও approved credentials এখনও পাওয়া যায়নি।

## যাচাই করা সরকারি উৎস

- Target API: https://developer.android.com/google/play/requirements/target-sdk
- User Data: https://support.google.com/googleplay/android-developer/answer/10144311
- AI content/reporting: https://support.google.com/googleplay/android-developer/answer/13985936
- Account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- Foreground service declaration: https://support.google.com/googleplay/android-developer/answer/13392821
- New personal account testing: https://support.google.com/googleplay/android-developer/answer/14151465
- OAuth verification: https://support.google.com/cloud/answer/13464321
- Gemini terms: https://ai.google.dev/gemini-api/terms
- Meta Pages API: https://developers.facebook.com/documentation/pages-api
- Meta comments: https://developers.facebook.com/documentation/pages-api/comments-mentions

কোনো app কখনো সরানো হবে না এমন নিশ্চয়তা দেওয়া সম্ভব নয়। সঠিক implementation, বাস্তব support, শর্তমাফিক data processing, testing এবং reviewer feedback অনুযায়ী সংশোধন চালিয়ে যেতে হবে।

0.3.1: সাধারণ টেমপ্লেটের আলাদা অন/অফ সুইচ, ডিফল্ট বন্ধ। নিজের keyword rule ও AI mode চালু রাখার জন্য এই সাধারণ উত্তর চালু করার প্রয়োজন নেই। 0.3.0-এর ওপর সরাসরি update করুন।

0.3.2: প্রতিটি notification-এর সম্পূর্ণ history নয়, সর্বশেষ incoming message দিয়ে rule মেলে। নিজের outgoing reply ও RemoteInput history বাদ যায়। একই message event-এ auto reply একবারই চালু হয়; পরবর্তী সত্যিকারের নতুন message আলাদা। সাধারণ উত্তর এখনও কেবল template mode-এ কোনো rule না মিললে এবং তার নিজস্ব সুইচ চালু থাকলে প্রযোজ্য।

0.3.3: WhatsApp Personal ও Business-এর মধ্যে নিজের auto reply ফিরে এসে loop হওয়ার ক্ষেত্র অন্তর্ভুক্ত। এই installation থেকে পাঠানো যেকোনো উত্তরের একই লেখা সব নির্বাচিত messaging app-এ ১০ মিনিট echo হিসেবে বাদ যায়, sender/timestamp নতুন হলেও। একই incoming লেখা একই sender/app-এর জন্য ১০ মিনিটে একবার নেওয়া হয়; একই chat-এ এক মিনিটে একবার auto send এবং একই উত্তর ১০ মিনিটে একবার। এক মিনিটে ৫ auto-send attempt-এর পরে আরও চেষ্টা হলে সব auto mode বন্ধ হয়। বৈধ contact একই লেখা ফেরত পাঠালেও ওই বিরতিতে auto reply নাও যাবে; manual reply ব্যবহার করা যায়। Update-এর পরে auto mode দুটো বন্ধ থাকবে, যাচাই করে নিজে চালু করতে হবে। নিয়ম ও Google login মুছবে না।
