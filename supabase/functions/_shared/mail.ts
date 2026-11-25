import type { InviteMail } from './api_handler.ts';

const roles:Record<string,string>={ADMIN:'Quản trị',EDITOR:'Có thể chỉnh sửa',VIEWER:'Chỉ xem'};
const escape=(text:string)=>text.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));

/** Subject and body of the invitation email; names come from users, so they are escaped. */
export function inviteMail(mail:InviteMail){
 const role=roles[mail.role]??roles.EDITOR;
 const subject=`${mail.inviter} mời bạn vào "${mail.base}" trên 3DDK Tasks`;
 const text=`${mail.inviter} đã mời bạn vào cơ sở dữ liệu "${mail.base}" với quyền ${role}.

Mở app 3DDK Tasks và đăng nhập bằng email ${mail.to}. Lời mời hiện ngay trên màn chính, chạm "Xem" rồi "Tham gia".`;
 const html=`<p><b>${escape(mail.inviter)}</b> đã mời bạn vào cơ sở dữ liệu <b>${escape(mail.base)}</b> với quyền <b>${escape(role)}</b>.</p><p>Mở app 3DDK Tasks và đăng nhập bằng email <b>${escape(mail.to)}</b>. Lời mời hiện ngay trên màn chính: chạm <b>Xem</b> rồi <b>Tham gia</b>.</p>`;
 return {to:[mail.to],subject,text,html};
}
