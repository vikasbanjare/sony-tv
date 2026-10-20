# Sources

`original-plan.md` is the research plan this lab was built from. Where it and this file disagree, this file is newer.

Checked 2026-10-20. The Sony India pages refuse simple fetches (HTTP 403), so they were read through a
browser-style scraper. Re-check the downloads page before acting on firmware: it changes.

## Sony, official

| Page | What it said on 2026-10-20 |
|---|---|
| [K-55S30 downloads](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/downloads) | One item: "Firmware update to v6120900201 for Asia Pacific", released 07/10/2026 |
| [Firmware 6120900201 details](https://www.sony.co.in/electronics/support/lcd-tvs-android-bravia-3-s30-/k-55s30/software/00357875) | Applies to K-43S30, K-50S30, **K-55S30**, K-55S30B, K-65S30, K-65S30B, K-75S30, K-75S30B, K-85S30. "Only for use with TV sold in Asia Pacific." File `sony_atvotafull_braviabf1_6120900201_www_auth.zip`, 1,551,492,152 bytes. Version check: HOME > Help, number under the model name; 6120900201 or higher is current |
| [K-55S30 specifications](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/specifications) | See `device.md` |
| [K-55S30 manuals](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/manuals) | Help Guide (web manual), released 20/11/2025 |
| [Help Guide](https://helpguide.sony.net/tv/lga2/v1/en-142/index.html) · [picture](https://helpguide.sony.net/tv/lga2/v1/en-142/01-03-09_01.html) · [sound](https://helpguide.sony.net/tv/lga2/v1/en-142/01-03-09_02.html) · [print version](https://helpguide.sony.net/tv/lga2/v1/en-142/print.html) | Menu items in `device.md`. Covers all models/regions |
| [Article 00276489](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/articles/00276489) | Generic: updates are on the Downloads page; none listed means none offered |
| [BRAVIA Professional REST API reference](https://pro-bravia.sony.net/remote-display-control/rest-api/reference/) | Lists `video` getPictureQualitySettings (v1.0, auth level none) and v1.1 |
| [REST API structure](https://pro-bravia.sony.net/remote-display-control/rest-api/structure/) | Pre-Shared Key in `X-Auth-PSK`; its service table leaves out `video` |

No Sony page says the professional REST API works on consumer 2024 models.

## Unofficial, not verified

- A Facebook group post (search snippet only, page didn't load) says Sony removed the Pre-Shared Key
  option on BRAVIA 3 "K-**S30" models: https://www.facebook.com/groups/companion/posts/3797303497154639/
- A Sony community thread asks how to use IP control without a PSK:
  https://ca.community.sony.com/s/question/0D5Dp00000ZYLbOKAX/

`scripts/bravia-rest-probe.sh` settles this on the actual TV.

## Projects

See `../config/repos.tsv` (pinned commits) and `../reports/compatibility.md` (what each one is and whether it applies).
