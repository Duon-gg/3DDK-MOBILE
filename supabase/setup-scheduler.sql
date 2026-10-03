-- Run once after putting project_url and dispatch_secret into Supabase Vault.
create extension if not exists pg_cron;
create extension if not exists pg_net;
select cron.unschedule(jobid) from cron.job where jobname='3ddk-notifications';
select cron.schedule('3ddk-notifications','* * * * *',$job$
 select net.http_post(
  url := (select decrypted_secret from vault.decrypted_secrets where name='project_url') || '/functions/v1/dispatch',
  headers := jsonb_build_object('Content-Type','application/json','x-dispatch-secret',(select decrypted_secret from vault.decrypted_secrets where name='dispatch_secret')),
  body := '{}'::jsonb, timeout_milliseconds := 60000
 );
$job$);
